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
package org.neo4j.cypher.internal.ir

import org.neo4j.cypher.internal.expressions.RelTypeName
import org.neo4j.cypher.internal.expressions.SemanticDirection
import org.neo4j.cypher.internal.expressions.ShortestPathsPatternPart
import org.neo4j.cypher.internal.ir.ExhaustivePathPattern.NodeConnections
import org.neo4j.cypher.internal.util.NonEmptyList
import org.neo4j.cypher.internal.util.Repetition
import org.neo4j.cypher.internal.util.Rewritable

/**
 * Part of a pattern that is connecting nodes (as in "connected components").
 * This is a generalisation of relationships.
 *
 * The implicit contract holds that
 *
 * boundaryNodes == (left, right)
 * boundaryNodesSet == Set(left, right)
 */
sealed trait NodeConnection {

  val left: String
  val right: String

  /**
   * The nodes connected by this node connection. That is, the outer-most nodes in thist part of the parttern.
   */
  val boundaryNodes: (String, String)

  lazy val boundaryNodesSet: Set[String] = Set(left, right)

  def coveredIds: Set[String]

  def otherSide(node: String): String =
    if (node == left) {
      right
    } else if (node == right) {
      left
    } else {
      throw new IllegalArgumentException(
        s"Did not provide either side as an argument to otherSide. Rel: $this, argument: $node"
      )
    }

  /**
   * @return A Cypher representation of this node connection
   */
  def solvedString: String
}

/**
 * This is a node connection that is not restricted by a selector.
 */
sealed trait ExhaustiveNodeConnection extends NodeConnection {

  /**
   * @return same as solvedString, but omitting the left node of the node connection
   */
  def solvedStringSuffix: String
}

object ExhaustiveNodeConnection {

  /**
   * A Cypher String from the given node connections forming a path pattern, left to right.
   */
  def solvedString(ncs: Seq[ExhaustiveNodeConnection]): String = {
    (ncs.head.solvedString +: ncs.tail.map(_.solvedStringSuffix)).mkString("")
  }
}

final case class PatternRelationship(
  name: String,
  boundaryNodes: (String, String),
  dir: SemanticDirection,
  types: Seq[RelTypeName],
  length: PatternLength
) extends ExhaustiveNodeConnection {

  def directionRelativeTo(node: String): SemanticDirection = if (node == left) dir else dir.reversed

  override lazy val coveredIds: Set[String] = Set(name, left, right)

  override val left: String = boundaryNodes._1
  override val right: String = boundaryNodes._2

  def inOrder: (String, String) = dir match {
    case SemanticDirection.INCOMING => (right, left)
    case _                          => (left, right)
  }

  override def toString: String = solvedString

  override def solvedString: String =
    s"(${boundaryNodes._1})$solvedStringSuffix"

  override def solvedStringSuffix: String = {
    val lArrow = if (dir == SemanticDirection.INCOMING) "<" else ""
    val rArrow = if (dir == SemanticDirection.OUTGOING) ">" else ""
    val typesStr =
      if (types.isEmpty) {
        ""
      } else {
        types.map(_.name).mkString(":", "|", "")
      }
    val lengthStr = length match {
      case SimplePatternLength              => ""
      case VarPatternLength(1, None)        => "*"
      case VarPatternLength(x, None)        => s"*$x.."
      case VarPatternLength(min, Some(max)) => s"*$min..$max"
    }
    s"$lArrow-[$name$typesStr$lengthStr]-$rArrow(${boundaryNodes._2})"
  }
}

object PatternRelationship {

  implicit val byName: Ordering[PatternRelationship] = Ordering.by { patternRel: PatternRelationship =>
    patternRel.name
  }
}

sealed trait PatternLength {
  def isSimple: Boolean

  def intersect(patternLength: PatternLength): PatternLength
}

case object SimplePatternLength extends PatternLength {
  def isSimple = true

  override def intersect(patternLength: PatternLength): PatternLength = SimplePatternLength
}

final case class VarPatternLength(min: Int, max: Option[Int]) extends PatternLength {
  def isSimple = false

  override def intersect(patternLength: PatternLength): PatternLength = patternLength match {
    case VarPatternLength(otherMin, otherMax) =>
      val newMax = Seq(max, otherMax).flatten.reduceOption(_ min _)
      VarPatternLength(min.max(otherMin), newMax)
    case _ => throw new IllegalArgumentException("VarPatternLength may only be intersected with VarPatternLength")
  }
}

object VarPatternLength {
  def unlimited: VarPatternLength = VarPatternLength(1, None)

  def fixed(length: Int): VarPatternLength = VarPatternLength(length, Some(length))
}

/**
 * Describes the connection between two juxtaposed nodes - one inside of a [[QuantifiedPathPattern]]
 * and the other one outside.
 */
case class NodeBinding(inner: String, outer: String) {
  override def toString: String = s"(inner=$inner, outer=$outer)"
}

/**
 * Describes a variable that is exposed from a [[org.neo4j.cypher.internal.expressions.QuantifiedPath]].
 *
 * @param singletonName the name of the singleton variable inside the QuantifiedPath.
 * @param groupName     the name of the group variable exposed outside of the QuantifiedPath.
 */
case class VariableGrouping(singletonName: String, groupName: String) {
  override def toString: String = s"(singletonName=$singletonName, groupName=$groupName)"
}

final case class QuantifiedPathPattern(
  leftBinding: NodeBinding,
  rightBinding: NodeBinding,
  patternRelationships: Seq[PatternRelationship],
  patternNodes: Set[String] = Set.empty,
  argumentIds: Set[String] = Set.empty,
  selections: Selections = Selections(),
  repetition: Repetition,
  nodeVariableGroupings: Set[VariableGrouping],
  relationshipVariableGroupings: Set[VariableGrouping]
) extends ExhaustiveNodeConnection {

  override val left: String = leftBinding.outer
  override val right: String = rightBinding.outer

  override val boundaryNodes: (String, String) = (left, right)

  override lazy val coveredIds: Set[String] = boundaryNodesSet ++ groupings

  override def toString: String =
    s"QPP($leftBinding, $rightBinding, $asQueryGraph, $repetition, $nodeVariableGroupings, $relationshipVariableGroupings)"

  override def solvedStringSuffix: String = {
    val where =
      if (selections.isEmpty) ""
      else selections.flatPredicates.map(QueryGraph.stringifier(_)).mkString(" WHERE ", " AND ", "")
    s" (${ExhaustiveNodeConnection.solvedString(patternRelationships)}$where)${repetition.solvedString} (${rightBinding.outer})"
  }

  override def solvedString: String =
    s"(${leftBinding.outer})$solvedStringSuffix"

  val dependencies: Set[String] = selections.predicates.flatMap(_.dependencies) ++ argumentIds

  val groupings: Set[String] = nodeVariableGroupings.map(_.groupName) ++ relationshipVariableGroupings.map(_.groupName)

  lazy val asQueryGraph: QueryGraph =
    QueryGraph
      .empty
      .addPatternRelationships(patternRelationships.toSet)
      .addPatternNodes(patternNodes.toList: _*)
      .addArgumentIds(argumentIds.toList)
      .addSelections(selections)
}

sealed trait PathPattern {

  /**
   * @return all quantified sub-path patterns contained in this path pattern
   */
  def allQuantifiedPathPatterns: Set[QuantifiedPathPattern]
}

/**
 * List of path patterns making up a graph pattern.
 */
case class PathPatterns(pathPatterns: List[PathPattern]) extends AnyVal {

  /**
   * @return all quantified sub-path patterns contained in these path patterns
   */
  def allQuantifiedPathPatterns: Set[QuantifiedPathPattern] =
    pathPatterns.view.flatMap(_.allQuantifiedPathPatterns).toSet
}

/**
 * A path pattern made of either a single node or a list of node connections.
 * It is exhaustive in that it represents all the paths matching this pattern.
 * Node connections are stored as a list, preserving the order of the pattern as expressed in the query.
 * Each connection contains a reference to the nodes it connects, effectively allowing us to reconstruct the alternating sequence of node and relationship patterns from this type.
 *
 * @tparam A In most cases, should be [[ExhaustiveNodeConnection]], but can be used to narrow down the type of node connections to [[PatternRelationship]] only.
 */
sealed trait ExhaustivePathPattern[+A <: ExhaustiveNodeConnection] extends PathPattern

object ExhaustivePathPattern {

  /**
   * A path pattern of length 0, made of a single node.
   *
   * @param name name of the variable bound to the node pattern
   */
  final case class SingleNode[A <: ExhaustiveNodeConnection](name: String) extends ExhaustivePathPattern[A] {
    override def allQuantifiedPathPatterns: Set[QuantifiedPathPattern] = Set.empty
  }

  /**
   * A path pattern of length 1 or more, made of at least one node connection.
   *
   * @param connections the connections making up the path pattern, in the order in which they appear in the original query.
   * @tparam A In most cases, should be [[ExhaustiveNodeConnection]], but can be used to narrow down the type of node connections to [[PatternRelationship]] only.
   */
  final case class NodeConnections[+A <: ExhaustiveNodeConnection](connections: NonEmptyList[A])
      extends ExhaustivePathPattern[A] {

    override def allQuantifiedPathPatterns: Set[QuantifiedPathPattern] = {
      connections.toSet[ExhaustiveNodeConnection].collect {
        case qpp: QuantifiedPathPattern => qpp
      }
    }
  }
}

/**
 * A path pattern, its predicates, and a selector limiting the number of paths to find.
 *
 * @param pathPattern path pattern for which we want to find solutions
 * @param selections  so-called "pre-filters", predicates that are applied to the path pattern as part of the path finding algorithm
 * @param selector    path selector such as ANY k, SHORTEST k, or SHORTEST k GROUPS, defining the type of path finding algorithm as well as the number paths to find
 */
final case class SelectivePathPattern(
  pathPattern: NodeConnections[ExhaustiveNodeConnection],
  selections: Selections,
  selector: SelectivePathPattern.Selector
) extends PathPattern with NodeConnection {
  override def allQuantifiedPathPatterns: Set[QuantifiedPathPattern] = pathPattern.allQuantifiedPathPatterns

  override val left: String = pathPattern.connections.head.left
  override val right: String = pathPattern.connections.last.right
  override val boundaryNodes: (String, String) = (left, right)

  override def coveredIds: Set[String] = pathPattern.connections.toSet[NodeConnection].flatMap(_.coveredIds)

  val dependencies: Set[String] = selections.predicates.flatMap(_.dependencies)

  def solvedString: String = {
    val where =
      if (selections.isEmpty) ""
      else selections.flatPredicates.map(QueryGraph.stringifier(_)).mkString(" WHERE ", " AND ", "")

    s"${selector.solvedString} (${ExhaustiveNodeConnection.solvedString(pathPattern.connections.toIndexedSeq)}$where)"
  }
}

object SelectivePathPattern {

  /**
   * Defines the paths to find for each combination of start and end nodes.
   */
  sealed trait Selector {

    /**
     * @return A Cypher representation of this selector
     */
    def solvedString: String
  }

  object Selector {

    /**
     * Finds up to k paths arbitrarily.
     */
    case class Any(k: Long) extends Selector {
      override def solvedString: String = s"ANY $k"
    }

    /**
     * Returns the shortest, second-shortest, etc. up to k paths.
     * If there are multiple paths of same length, picks arbitrarily.
     */
    case class Shortest(k: Long) extends Selector {
      override def solvedString: String = s"SHORTEST $k"
    }

    /**
     * Finds all shortest paths, all second shortest paths, etc. up to all Kth shortest paths.
     */
    case class ShortestGroups(k: Long) extends Selector {
      override def solvedString: String = s"SHORTEST $k GROUPS"
    }
  }
}

//noinspection ZeroIndexToHead
final case class ShortestRelationshipPattern(name: Option[String], rel: PatternRelationship, single: Boolean)(
  val expr: ShortestPathsPatternPart
) extends PathPattern with Rewritable {

  def dup(children: Seq[AnyRef]): this.type =
    copy(
      children(0).asInstanceOf[Option[String]],
      children(1).asInstanceOf[PatternRelationship],
      children(2).asInstanceOf[Boolean]
    )(expr).asInstanceOf[this.type]

  def isFindableFrom(symbols: Set[String]): Boolean = symbols.contains(rel.left) && symbols.contains(rel.right)

  def availableSymbols: Set[String] = name.toSet ++ rel.coveredIds

  override def allQuantifiedPathPatterns: Set[QuantifiedPathPattern] = Set.empty
}

object ShortestRelationshipPattern {

  implicit val byRelName: Ordering[ShortestRelationshipPattern] = Ordering.by { sp: ShortestRelationshipPattern =>
    sp.rel
  }
}
