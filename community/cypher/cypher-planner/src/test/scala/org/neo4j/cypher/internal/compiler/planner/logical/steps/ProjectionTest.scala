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
package org.neo4j.cypher.internal.compiler.planner.logical.steps

import org.neo4j.cypher.internal.ast.ASTAnnotationMap.ASTAnnotationMap
import org.neo4j.cypher.internal.ast.semantics.ExpressionTypeInfo
import org.neo4j.cypher.internal.ast.semantics.SemanticTable
import org.neo4j.cypher.internal.compiler.planner.LogicalPlanningTestSupport
import org.neo4j.cypher.internal.compiler.planner.logical.LogicalPlanningContext
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.ir.QueryGraph
import org.neo4j.cypher.internal.ir.RegularQueryProjection
import org.neo4j.cypher.internal.ir.RegularSinglePlannerQuery
import org.neo4j.cypher.internal.logical.plans.LogicalPlan
import org.neo4j.cypher.internal.logical.plans.Projection
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite

class ProjectionTest extends CypherFunSuite with LogicalPlanningTestSupport {

  test("should add projection for expressions not already covered") {
    // given
    val projections = Map("42" -> literalInt(42))

    val (context, startPlan) = queryGraphWith(projectionsMap = projections)

    // when
    val result = projection(startPlan, projections, Some(projections), keepAllColumns = true, context)

    // then
    result should equal(Projection(startPlan, Set.empty, projections.map { case (key, value) => varFor(key) -> value }))
    context.staticComponents.planningAttributes.solveds.get(result.id).asSinglePlannerQuery.horizon should equal(
      RegularQueryProjection(projections)
    )
  }

  test("should mark as solved according to projectionsToMarkSolved argument") {
    // given
    val projections = Map("42" -> literalInt(42), "43" -> literalInt(43))
    val projectionsToMarkSolved = Map("42" -> literalInt(42))

    val (context, startPlan) = queryGraphWith(projectionsMap = projections)

    // when
    val result = projection(startPlan, projections, Some(projectionsToMarkSolved), keepAllColumns = true, context)

    // then
    result should equal(Projection(startPlan, Set.empty, projections.map { case (key, value) => varFor(key) -> value }))
    context.staticComponents.planningAttributes.solveds.get(result.id).asSinglePlannerQuery.horizon should equal(
      RegularQueryProjection(projectionsToMarkSolved)
    )
  }

  test("does not add projection when not needed") {
    // given
    val projections = Map("n" -> varFor("n"))
    val (context, startPlan) = queryGraphWith(projectionsMap = projections)

    // when
    val result = projection(startPlan, projections, Some(projections), keepAllColumns = true, context)

    // then
    result should equal(startPlan)
    context.staticComponents.planningAttributes.solveds.get(result.id).asSinglePlannerQuery.horizon should equal(
      RegularQueryProjection(projections)
    )
  }

  test("only adds the set difference of projections needed") {
    // given
    val projections = Map("n" -> varFor("n"), "42" -> literalInt(42))
    val (context, startPlan) = queryGraphWith(projectionsMap = projections)

    // when
    val result = projection(startPlan, projections, Some(projections), keepAllColumns = true, context)

    // then
    val actualProjections = Map("42" -> literalInt(42))
    result should equal(Projection(
      startPlan,
      Set.empty,
      actualProjections.map { case (key, value) => varFor(key) -> value }
    ))
    context.staticComponents.planningAttributes.solveds.get(result.id).asSinglePlannerQuery.horizon should equal(
      RegularQueryProjection(projections)
    )
  }

  test("does projection when renaming columns") {
    // given
    val projections = Map("  n@34" -> varFor("n"))
    val (context, startPlan) = queryGraphWith(projectionsMap = projections)

    // when
    val result = projection(startPlan, projections, Some(projections), keepAllColumns = true, context)

    // then
    result should equal(Projection(startPlan, Set.empty, projections.map { case (key, value) => varFor(key) -> value }))
    context.staticComponents.planningAttributes.solveds.get(result.id).asSinglePlannerQuery.horizon should equal(
      RegularQueryProjection(projections)
    )
  }

  private def queryGraphWith(projectionsMap: Map[String, Expression]): (LogicalPlanningContext, LogicalPlan) = {
    val context = newMockedLogicalPlanningContext(
      planContext = newMockedPlanContext(),
      semanticTable = new SemanticTable(types = mock[ASTAnnotationMap[Expression, ExpressionTypeInfo]])
    )

    val ids = projectionsMap.keySet

    val plan =
      newMockedLogicalPlanWithSolved(
        context.staticComponents.planningAttributes,
        idNames = ids,
        solved = RegularSinglePlannerQuery(QueryGraph.empty.addPatternNodes(ids.toList: _*))
      )

    (context, plan)
  }
}
