/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.compiler.ast.convert.plannerQuery

import org.neo4j.cypher.internal.ast
import org.neo4j.cypher.internal.ast.Clause
import org.neo4j.cypher.internal.ast.Create
import org.neo4j.cypher.internal.ast.ProjectingUnionAll
import org.neo4j.cypher.internal.ast.ProjectingUnionDistinct
import org.neo4j.cypher.internal.ast.Query
import org.neo4j.cypher.internal.ast.SingleQuery
import org.neo4j.cypher.internal.ast.UnaliasedReturnItem
import org.neo4j.cypher.internal.ast.UnionAll
import org.neo4j.cypher.internal.ast.UnionDistinct
import org.neo4j.cypher.internal.ast.With
import org.neo4j.cypher.internal.ast.semantics.SemanticTable
import org.neo4j.cypher.internal.compiler.ast.convert.plannerQuery.ClauseConverters.addToLogicalPlanInput
import org.neo4j.cypher.internal.expressions.And
import org.neo4j.cypher.internal.expressions.NonPrefixedPatternPart
import org.neo4j.cypher.internal.expressions.Or
import org.neo4j.cypher.internal.expressions.Pattern
import org.neo4j.cypher.internal.ir.PlannerQuery
import org.neo4j.cypher.internal.ir.SinglePlannerQuery
import org.neo4j.cypher.internal.ir.UnionQuery
import org.neo4j.cypher.internal.util.ASTNode
import org.neo4j.cypher.internal.util.AnonymousVariableNameGenerator
import org.neo4j.cypher.internal.util.CancellationChecker
import org.neo4j.cypher.internal.util.Foldable.TraverseChildren
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.exceptions.InternalException

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer

object StatementConverters {

  /**
   * Convert an AST SingleQuery into an IR SinglePlannerQuery
   */
  private def toSinglePlannerQuery(
    q: SingleQuery,
    semanticTable: SemanticTable,
    anonymousVariableNameGenerator: AnonymousVariableNameGenerator,
    cancellationChecker: CancellationChecker,
    importedVariables: Set[String],
    nonTerminating: Boolean
  ): SinglePlannerQuery = {
    val allImportedVars = importedVariables ++ q.importWith.map((wth: With) =>
      wth.returnItems.items.map(_.name).toSet
    ).getOrElse(Set.empty)

    val builder = PlannerQueryBuilder(semanticTable, allImportedVars)
    addClausesToPlannerQueryBuilder(
      q.clauses,
      builder,
      anonymousVariableNameGenerator,
      cancellationChecker,
      nonTerminating
    ).build()
  }

  /**
   * Add all given clauses to a PlannerQueryBuilder and return the updated builder.
   */
  def addClausesToPlannerQueryBuilder(
    clauses: Seq[Clause],
    builder: PlannerQueryBuilder,
    anonymousVariableNameGenerator: AnonymousVariableNameGenerator,
    cancellationChecker: CancellationChecker,
    nonTerminating: Boolean
  ): PlannerQueryBuilder = {
    @tailrec
    def addClausesToPlannerQueryBuilderRec(clauses: Seq[Clause], builder: PlannerQueryBuilder): PlannerQueryBuilder =
      if (clauses.isEmpty)
        builder
      else {
        cancellationChecker.throwIfCancelled()
        val clause = clauses.head
        val nextClauses = clauses.tail
        val nextClause = nextClauses.headOption
        val newBuilder =
          addToLogicalPlanInput(
            builder,
            clause,
            nextClause,
            anonymousVariableNameGenerator,
            cancellationChecker,
            nonTerminating
          )
        addClausesToPlannerQueryBuilderRec(nextClauses, newBuilder)
      }

    addClausesToPlannerQueryBuilderRec(flattenCreates(clauses), builder)
  }

  private val NODE_BLACKLIST: Set[Class[_ <: ASTNode]] = Set(
    classOf[And],
    classOf[Or],
    classOf[UnaliasedReturnItem],
    classOf[UnionAll],
    classOf[UnionDistinct]
  )

  private def findBlacklistedNodes(query: Query): Seq[ASTNode] = {
    query.folder.treeFold(Seq.empty[ASTNode]) {
      case node: ASTNode if NODE_BLACKLIST.contains(node.getClass) =>
        acc => TraverseChildren(acc :+ node)
    }
  }

  def toPlannerQuery(
    query: Query,
    semanticTable: SemanticTable,
    anonymousVariableNameGenerator: AnonymousVariableNameGenerator,
    cancellationChecker: CancellationChecker,
    importedVariables: Set[String] = Set.empty,
    rewrite: Boolean = true,
    nonTerminating: Boolean = false
  ): PlannerQuery = {
    val rewrittenQuery =
      if (rewrite) query.endoRewrite(CreateIrExpressions(anonymousVariableNameGenerator, semanticTable)) else query
    val nodes = findBlacklistedNodes(rewrittenQuery)
    require(nodes.isEmpty, "Found a blacklisted AST node: " + nodes.head.toString)

    rewrittenQuery match {
      case singleQuery: SingleQuery =>
        toSinglePlannerQuery(
          singleQuery,
          semanticTable,
          anonymousVariableNameGenerator,
          cancellationChecker,
          importedVariables,
          nonTerminating
        )

      case unionQuery: ast.ProjectingUnion =>
        val lhs: PlannerQuery =
          toPlannerQuery(
            unionQuery.lhs,
            semanticTable,
            anonymousVariableNameGenerator,
            cancellationChecker,
            importedVariables,
            rewrite = false,
            nonTerminating = true
          )
        val rhs: SinglePlannerQuery =
          toSinglePlannerQuery(
            unionQuery.rhs,
            semanticTable,
            anonymousVariableNameGenerator,
            cancellationChecker,
            importedVariables,
            nonTerminating = true
          )

        val distinct = unionQuery match {
          case _: ProjectingUnionAll      => false
          case _: ProjectingUnionDistinct => true
        }

        UnionQuery(lhs, rhs, distinct, unionQuery.unionMappings)
      case _ =>
        throw new InternalException(s"Received an AST-clause that has no representation the QG: $rewrittenQuery")
    }
  }

  /**
   * Flatten consecutive CREATE clauses into one.
   *
   *   CREATE (a) CREATE (b) => CREATE (a),(b)
   */
  def flattenCreates(clauses: Seq[Clause]): Seq[Clause] = {
    val builder = ArrayBuffer.empty[Clause]
    var prevCreate: Option[(Seq[NonPrefixedPatternPart], InputPosition)] = None
    for (clause <- clauses) {
      (clause, prevCreate) match {
        case (c: Create, None) =>
          prevCreate = Some((c.pattern.patternParts, c.position))

        case (c: Create, Some((prevParts, pos))) =>
          prevCreate = Some((prevParts ++ c.pattern.patternParts, pos))

        case (nonCreate, Some((prevParts, pos))) =>
          builder += Create(Pattern.ForUpdate(prevParts)(pos))(pos)
          builder += nonCreate
          prevCreate = None

        case (nonCreate, None) =>
          builder += nonCreate
      }
    }
    for ((prevParts, pos) <- prevCreate)
      builder += Create(Pattern.ForUpdate(prevParts)(pos))(pos)
    builder
  }.toSeq
}
