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
package org.neo4j.cypher.internal.runtime.spec.tests

import org.neo4j.cypher.internal.CypherRuntime
import org.neo4j.cypher.internal.RuntimeContext
import org.neo4j.cypher.internal.runtime.spec.Edition
import org.neo4j.cypher.internal.runtime.spec.LogicalQueryBuilder
import org.neo4j.cypher.internal.runtime.spec.RuntimeTestSuite
import org.neo4j.graphdb.RelationshipType

import scala.util.Random

abstract class UndirectedRelationshipByElementIdSeekTestBase[CONTEXT <: RuntimeContext](
  edition: Edition[CONTEXT],
  runtime: CypherRuntime[CONTEXT],
  sizeHint: Int
) extends RuntimeTestSuite[CONTEXT](edition, runtime) {

  private val random = new Random(77)

  private def quote(s: String): String = s"'$s'"

  test("should find single relationship") {
    // given
    val (_, relationships) = given { circleGraph(17) }
    val relToFind = relationships(random.nextInt(relationships.length))

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r", "x", "y")
      .undirectedRelationshipByElementIdSeek("r", "x", "y", Set.empty, quote(relToFind.getElementId))
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("r", "x", "y").withRows(Seq(
      Array(relToFind, relToFind.getStartNode, relToFind.getEndNode),
      Array(relToFind, relToFind.getEndNode, relToFind.getStartNode)
    ))
  }

  test("should not find non-existing relationship") {
    // given
    given { circleGraph(17) }
    val toNotFind = "bad-id"

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r", "x", "y")
      .undirectedRelationshipByElementIdSeek("r", "x", "y", Set.empty, quote(toNotFind))
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("r", "x", "y").withNoRows()
  }

  test("should find multiple relationships") {
    // given
    val (_, relationships) = given { circleGraph(sizeHint) }
    val toFind = (1 to 5).map(_ => relationships(random.nextInt(relationships.length)))
    restartTx()

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r", "x", "y")
      .undirectedRelationshipByElementIdSeek("r", "x", "y", Set.empty, toFind.map(_.getElementId).map(quote): _*)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    val expected = toFind.flatMap(r => {
      val rel = tx.getRelationshipById(r.getId)
      Seq(Array(rel, rel.getStartNode, rel.getEndNode), Array(rel, rel.getEndNode, rel.getStartNode))
    })
    runtimeResult should beColumns("r", "x", "y").withRows(expected)
  }

  test("should find some relationships and not others") {
    // given
    val (_, relationships) = given { circleGraph(sizeHint) }
    val toFind = (1 to 5).map(_ => relationships(random.nextInt(relationships.length)))
    val toNotFind1 = relationships.last.getElementId.drop(1)
    val toNotFind2 = toNotFind1.drop(1)
    val relationshipsToLookFor = toNotFind1 +: toFind.map(_.getElementId) :+ toNotFind2
    restartTx()

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r", "x", "y")
      .undirectedRelationshipByElementIdSeek("r", "x", "y", Set.empty, relationshipsToLookFor.map(quote): _*)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    val expected = toFind.flatMap(r => {
      val rel = tx.getRelationshipById(r.getId)
      Seq(Array(rel, rel.getStartNode, rel.getEndNode), Array(rel, rel.getEndNode, rel.getStartNode))
    })
    runtimeResult should beColumns("r", "x", "y").withRows(expected)
  }

  test("should handle relById + filter") {
    // given
    val (_, relationships) = given { circleGraph(sizeHint) }
    val toSeekFor = (1 to 5).map(_ => relationships(random.nextInt(relationships.length)))
    val toFind = toSeekFor(random.nextInt(toSeekFor.length))
    restartTx()

    val attachedToFind = tx.getRelationshipById(toFind.getId)
    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r", "x", "y")
      .filter(s"elementId(r) = '${attachedToFind.getElementId}'")
      .undirectedRelationshipByElementIdSeek("r", "x", "y", Set.empty, toSeekFor.map(_.getElementId).map(quote): _*)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("r", "x", "y").withRows(Seq(
      Array(attachedToFind, attachedToFind.getStartNode, attachedToFind.getEndNode),
      Array(attachedToFind, attachedToFind.getEndNode, attachedToFind.getStartNode)
    ))
  }

  test("should handle limit + sort") {
    val (nodes, relationships) = given {
      circleGraph(sizeHint, "A")
    }
    val limit = 1

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .apply()
      .|.limit(limit)
      .|.sort("r ASC", "x ASC")
      .|.undirectedRelationshipByElementIdSeek("r", "x", "y", Set("a1"), quote(relationships.head.getElementId))
      .allNodeScan("a1")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("x").withRows(nodes.map(_ => Array[Any](nodes.head)))
  }

  test("should handle continuation from single undirectedRelationshipByElementIdSeek") {
    // given
    val nodesPerLabel = sizeHint / 4
    val (r, nodes) = given {
      val (_, _, rs, _) = bidirectionalBipartiteGraph(nodesPerLabel, "A", "B", "R", "R2")
      val r = rs.head
      val nodes = Seq(r.getStartNode, r.getEndNode)
      (r, nodes)
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r", "x", "y")
      .nonFuseable()
      .expand("(x)-[r2]->(y2)")
      .undirectedRelationshipByElementIdSeek("r", "x", "y", Set.empty, quote(r.getElementId))
      .build()

    val runtimeResult = execute(logicalQuery, runtime)
    val expected = for {
      Seq(x, y) <- nodes.permutations.toSeq
      _ <- 0 until nodesPerLabel
    } yield Array(r, x, y)

    runtimeResult should beColumns("r", "x", "y").withRows(expected)
  }

  test("should handle continuation from multiple undirectedRelationshipByElementIdSeek") {
    // given
    val nodesPerLabel = 20
    val (rs, nodes) = given {
      val (_, _, rs, _) = bidirectionalBipartiteGraph(nodesPerLabel, "A", "B", "R", "R2")
      val nodes = rs.map(r => r -> Seq(r.getStartNode, r.getEndNode)).toMap
      (rs, nodes)
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r", "x", "y")
      .nonFuseable()
      .expand("(x)-[r2]->(y2)")
      .undirectedRelationshipByElementIdSeek("r", "x", "y", Set.empty, rs.map(_.getElementId).map(quote): _*)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)
    val expected = for {
      r <- rs
      Seq(x, y) <- nodes(r).permutations.toSeq
      _ <- 0 until nodesPerLabel
    } yield Array(r, x, y)

    runtimeResult should beColumns("r", "x", "y").withRows(expected)
  }

  test("should only find loop once") {
    // given
    val relToFind = given {
      val a = tx.createNode()
      a.createRelationshipTo(a, RelationshipType.withName("R"))
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r", "x", "y")
      .undirectedRelationshipByElementIdSeek("r", "x", "y", Set.empty, quote(relToFind.getElementId))
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("r", "x", "y").withRows(Seq(
      Array(relToFind, relToFind.getStartNode, relToFind.getEndNode)
    ))
  }

  test("should only find loop once, many ids") {
    // given
    val relToFind = given {
      val a = tx.createNode()
      a.createRelationshipTo(a, RelationshipType.withName("R"))
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r", "x", "y")
      .undirectedRelationshipByElementIdSeek(
        "r",
        "x",
        "y",
        Set.empty,
        Seq.fill(10)(relToFind.getElementId).map(quote): _*
      )
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("r", "x", "y").withRows(Seq.fill(10)(
      Array(relToFind, relToFind.getStartNode, relToFind.getEndNode)
    ))
  }
}
