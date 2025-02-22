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
package org.neo4j.cypher.internal.compiler.planner.logical

import org.neo4j.cypher.internal.ast.AstConstructionTestSupport
import org.neo4j.cypher.internal.compiler.helpers.LogicalPlanBuilder
import org.neo4j.cypher.internal.compiler.planner.BeLikeMatcher
import org.neo4j.cypher.internal.compiler.planner.LogicalPlanningIntegrationTestSupport
import org.neo4j.cypher.internal.compiler.planner.logical.steps.QuerySolvableByGetDegree.SetExtractor
import org.neo4j.cypher.internal.expressions.Ands
import org.neo4j.cypher.internal.expressions.ContainerIndex
import org.neo4j.cypher.internal.expressions.GetDegree
import org.neo4j.cypher.internal.expressions.HasDegreeGreaterThan
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.MultiRelationshipPathStep
import org.neo4j.cypher.internal.expressions.NilPathStep
import org.neo4j.cypher.internal.expressions.NodePathStep
import org.neo4j.cypher.internal.expressions.Not
import org.neo4j.cypher.internal.expressions.Ors
import org.neo4j.cypher.internal.expressions.PathExpression
import org.neo4j.cypher.internal.expressions.ReduceExpression
import org.neo4j.cypher.internal.expressions.RelTypeName
import org.neo4j.cypher.internal.expressions.SemanticDirection
import org.neo4j.cypher.internal.expressions.SemanticDirection.OUTGOING
import org.neo4j.cypher.internal.expressions.SignedDecimalIntegerLiteral
import org.neo4j.cypher.internal.expressions.SingleRelationshipPathStep
import org.neo4j.cypher.internal.expressions.Variable
import org.neo4j.cypher.internal.expressions.functions.Head
import org.neo4j.cypher.internal.ir.EagernessReason
import org.neo4j.cypher.internal.ir.NoHeaders
import org.neo4j.cypher.internal.logical.builder.AbstractLogicalPlanBuilder.Predicate
import org.neo4j.cypher.internal.logical.builder.AbstractLogicalPlanBuilder.andsReorderable
import org.neo4j.cypher.internal.logical.builder.AbstractLogicalPlanBuilder.createNode
import org.neo4j.cypher.internal.logical.builder.AbstractLogicalPlanBuilder.createNodeWithProperties
import org.neo4j.cypher.internal.logical.builder.AbstractLogicalPlanBuilder.createPattern
import org.neo4j.cypher.internal.logical.builder.AbstractLogicalPlanBuilder.createRelationship
import org.neo4j.cypher.internal.logical.plans.Argument
import org.neo4j.cypher.internal.logical.plans.Ascending
import org.neo4j.cypher.internal.logical.plans.Expand.ExpandAll
import org.neo4j.cypher.internal.logical.plans.Expand.VariablePredicate
import org.neo4j.cypher.internal.logical.plans.IndexOrderAscending
import org.neo4j.cypher.internal.logical.plans.IndexOrderNone
import org.neo4j.cypher.internal.logical.plans.NestedPlanCollectExpression
import org.neo4j.cypher.internal.logical.plans.NestedPlanExistsExpression
import org.neo4j.cypher.internal.logical.plans.NestedPlanGetByNameExpression
import org.neo4j.cypher.internal.logical.plans.Projection
import org.neo4j.cypher.internal.logical.plans.RollUpApply
import org.neo4j.cypher.internal.logical.plans.Selection
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite
import org.neo4j.graphdb.schema.IndexType

import scala.collection.immutable.ListSet

class SubqueryExpressionPlanningIntegrationTest extends CypherFunSuite with LogicalPlanningIntegrationTestSupport
    with AstConstructionTestSupport with BeLikeMatcher {

  private val NL = System.lineSeparator()

  private val planner = plannerBuilder()
    .setAllNodesCardinality(1000)
    .setLabelCardinality("B", 10)
    .setLabelCardinality("C", 10)
    .setLabelCardinality("D", 1)
    .setLabelCardinality("Foo", 10)
    .setLabelCardinality("Bar", 10)
    .setLabelCardinality("Person", 10)
    .setLabelCardinality("Dog", 10)
    .setLabelCardinality("ComedyClub", 10)
    .setLabelCardinality("User", 10)
    .setLabelCardinality("Label", 10)
    .setLabelCardinality("UniqueLabel", 100)
    .setLabelCardinality("TextLabel", 10)
    .setLabelCardinality("M", 10)
    .setLabelCardinality("O", 10)
    .setLabelCardinality("FewProps", 100)
    .setLabelCardinality("SomeProps", 100)
    .setLabelCardinality("ManyProps", 100)
    .setAllRelationshipsCardinality(100)
    .setRelationshipCardinality("()-[]->()", 50)
    .setRelationshipCardinality("()-[:REL]-()", 10)
    .setRelationshipCardinality("()-[:X]-()", 10)
    .setRelationshipCardinality("()-[:X]-(:Foo)", 10)
    .setRelationshipCardinality("()-[:X]-(:Bar)", 10)
    .setRelationshipCardinality("()-[:X]-(:B)", 10)
    .setRelationshipCardinality("(:Foo)-[]->()", 10)
    .setRelationshipCardinality("()-[]->(:Foo)", 10)
    .setRelationshipCardinality("()-[:Y]-()", 20)
    .setRelationshipCardinality("()-[:Y]-(:B)", 20)
    .setRelationshipCardinality("()-[]->(:B)", 10)
    .setRelationshipCardinality("()-[]->(:C)", 10)
    .setRelationshipCardinality("()-[]->(:D)", 10)
    .setRelationshipCardinality("()-[:KNOWS]-()", 10)
    .setRelationshipCardinality("()-[:HAS_DOG]-()", 10)
    .setRelationshipCardinality("(:Person)-[:KNOWS]-()", 10)
    .setRelationshipCardinality("(:Person)-[:HAS_DOG]-()", 10)
    .setRelationshipCardinality("(:Dog)-[:HAS_DOG]-()", 10)
    .setRelationshipCardinality("(:Person)-[:KNOWS]-(:Person)", 10)
    .setRelationshipCardinality("(:Person)-[:HAS_DOG]-(:Dog)", 10)
    .setRelationshipCardinality("()-[:WORKS_AT]-()", 10)
    .setRelationshipCardinality("(:ComedyClub)-[:WORKS_AT]-()", 10)
    .setRelationshipCardinality("()-[:FOLLOWS]-()", 10)
    .setRelationshipCardinality("(:User)-[:FOLLOWS]-()", 10)
    .setRelationshipCardinality("(:User)-[:FOLLOWS]->(:User)", 10)
    .setRelationshipCardinality("()-[:FOO]->()", 10)
    .setRelationshipCardinality("()-[:R]->()", 10)
    .setRelationshipCardinality("()-[:TextRel]->()", 10)
    .setRelationshipCardinality("()-[:R]->(:M)", 10)
    .setRelationshipCardinality("()-[:Q]->()", 10)
    .setRelationshipCardinality("()-[:Q]->(:O)", 10)
    .setRelationshipCardinality("(:FewProps)-[]-(:SomeProps)", 10)
    .setRelationshipCardinality("(:SomeProps)-[]-(:ManyProps)", 10)
    .addRelationshipIndex("REL", Seq("prop"), 1.0, 0.01)
    .addRelationshipIndex("TextRel", Seq("prop"), 1.0, 0.01, indexType = IndexType.TEXT)
    .addNodeIndex("Label", Seq("prop"), 1.0, 0.1, indexType = IndexType.RANGE)
    .addNodeIndex("UniqueLabel", Seq("prop"), 1.0, 0.01, isUnique = true, indexType = IndexType.RANGE)
    .addNodeIndex("TextLabel", Seq("prop"), 1.0, 0.01, indexType = IndexType.TEXT)
    .addNodeIndex("D", Seq("prop"), 1.0, 0.01)
    .addNodeIndex("FewProps", Seq("prop"), 1.0, 0.01)
    .addNodeIndex("SomeProps", Seq("prop"), 1.0, 0.1)
    .addNodeIndex("ManyProps", Seq("prop"), 1.0, 1.0)
    .build()

  test("should plan CREATE followed by container access with COUNT expression as an index") {
    val q = "CREATE (a) RETURN [1, 2, 3][COUNT { MATCH (x) }] AS result"
    val plan = planner.plan(q).stripProduceResults

    val resultExpr = containerIndex(
      listOfInt(1, 2, 3),
      NestedPlanGetByNameExpression(
        planner.subPlanBuilder()
          .nodeCountFromCountStore("anon_0", Seq(None))
          .build(),
        varFor("anon_0"),
        "COUNT { MATCH (x) }"
      )(pos)
    )

    plan shouldEqual planner.subPlanBuilder()
      .projection(project = Map("result" -> resultExpr), discard = Set("a"))
      .eager(ListSet(EagernessReason.Unknown))
      .create(createNode("a"))
      .argument()
      .build()
  }

  test("should plan CREATE followed by container access with GetDegree as an index") {
    val q = "CREATE (a) RETURN [1, 2, 3][COUNT { MATCH (a)-->() }] AS result"
    val plan = planner.plan(q).stripProduceResults

    val resultExpr = containerIndex(
      listOfInt(1, 2, 3),
      getDegree(varFor("a"), SemanticDirection.OUTGOING)
    )

    plan shouldEqual planner.subPlanBuilder()
      .projection(project = Map("result" -> resultExpr), discard = Set("a"))
      .create(createNode("a"))
      .argument()
      .build()
  }

  test("should plan multiple EXISTS with inequality predicate") {
    val q = "RETURN EXISTS { (a) } <> EXISTS { (b) } <> EXISTS { (c) } AS result"
    val plan = planner.plan(q).stripProduceResults
    plan should beLike {
      case Projection(_, _, projectExpressions) =>
        projectExpressions shouldBe Map(
          varFor("result") ->
            ands(
              not(equals(varFor("anon_2"), varFor("anon_3"))),
              not(equals(varFor("anon_4"), varFor("anon_5")))
            )
        )
    }
  }

  test("should solve plan for Collect Subquery Aggregation") {
    val plan = planner.plan(
      "MATCH (a:Person) RETURN COLLECT { MATCH (a)-[r:KNOWS]->(c) RETURN a } AS foo"
    ).stripProduceResults
    plan shouldBe planner.subPlanBuilder()
      .rollUpApply("foo", "a")
      .|.expandAll("(a)-[r:KNOWS]->(c)")
      .|.argument("a")
      .nodeByLabelScan("a", "Person", IndexOrderNone)
      .build()
  }

  test("should solve plan for Collect Subquery Aggregation with Union") {
    val plan = planner.plan(
      "MATCH (a:Person) RETURN COLLECT { MATCH (a)-[r:KNOWS]->(c) RETURN a AS b UNION MATCH (a)-[r:KNOWS]->(d) RETURN a AS b } AS foo"
    ).stripProduceResults
    plan shouldBe planner.subPlanBuilder()
      .rollUpApply("foo", "b")
      .|.distinct("b AS b", "a AS a")
      .|.union()
      .|.|.projection("b AS b")
      .|.|.projection("a AS b")
      .|.|.expandAll("(a)-[r:KNOWS]->(d)")
      .|.|.argument("a")
      .|.projection("b AS b")
      .|.projection("a AS b")
      .|.expandAll("(a)-[r:KNOWS]->(c)")
      .|.argument("a")
      .nodeByLabelScan("a", "Person", IndexOrderNone)
      .build()
  }

  test("should solve simple COLLECT expression") {
    val plan = planner.plan(
      "RETURN COLLECT { MATCH (f) RETURN f.name } AS friends"
    ).stripProduceResults
    plan shouldBe planner.subPlanBuilder()
      .rollUpApply("friends", "f.name")
      .|.projection("f.name AS `f.name`")
      .|.allNodeScan("f")
      .argument()
      .build()
  }

  test("should solve Collect Subquery Aggregation with CALL inside") {
    val plan = planner.plan(
      "MATCH (a:Person) RETURN COLLECT { MATCH (a)-[r:KNOWS]->(c) CALL { WITH a MATCH (a)-[:HAS_DOG]-(b) RETURN b} RETURN a } AS foo"
    ).stripProduceResults
    plan shouldBe planner.subPlanBuilder()
      .rollUpApply("foo", "a")
      .|.expandAll("(a)-[anon_0:HAS_DOG]-(b)")
      .|.expandAll("(a)-[r:KNOWS]->(c)")
      .|.argument("a")
      .nodeByLabelScan("a", "Person", IndexOrderNone)
      .build()
  }

  test("should plan COLLECT expression with multiple patterns") {
    val plan = planner.plan(
      "MATCH (a:Foo) RETURN COLLECT { MATCH (a)-[r]->(b), (a)<-[r2]-(b) RETURN a } AS bidirectionalConnections"
    ).stripProduceResults
    plan shouldBe planner.subPlanBuilder()
      .rollUpApply("bidirectionalConnections", "a")
      .|.filter("not r = r2")
      .|.expandInto("(a)-[r]->(b)")
      .|.expandAll("(a)<-[r2]-(b)")
      .|.argument("a")
      .nodeByLabelScan("a", "Foo")
      .build()
  }

  test("should plan COLLECT expression as equality check") {
    val plan = planner.plan(
      "MATCH (a:Foo) RETURN COLLECT { MATCH (a)-[r]->(b), (a)<-[r2]-(b) RETURN a.prop } = [1, 2, 3] AS foo"
    ).stripProduceResults
    plan shouldBe planner.subPlanBuilder()
      .projection(project = Seq("anon_0 = [1, 2, 3] AS foo"), discard = Set("a", "anon_0"))
      .rollUpApply("anon_0", "a.prop")
      .|.projection("a.prop AS `a.prop`")
      .|.filter("not r = r2")
      .|.expandInto("(a)-[r]->(b)")
      .|.expandAll("(a)<-[r2]-(b)")
      .|.argument("a")
      .nodeByLabelScan("a", "Foo")
      .build()
  }

  test("should plan COLLECT expression with multiple disconnected patterns") {
    val plan = planner.plan(
      "MATCH (a:Person), (b:Person) RETURN COLLECT { MATCH (a)-[r:KNOWS]->(c), (b)-[r2:KNOWS]->(d) WHERE c.name = d.name RETURN c.name } AS sameNameFriends"
    ).stripProduceResults
    plan shouldBe planner.subPlanBuilder()
      .rollUpApply("sameNameFriends", "c.name")
      .|.projection("cacheN[c.name] AS `c.name`")
      .|.filter("cacheNFromStore[c.name] = d.name", "not r = r2")
      .|.expandAll("(a)-[r:KNOWS]->(c)")
      .|.expandAll("(b)-[r2:KNOWS]->(d)")
      .|.argument("a", "b")
      .cartesianProduct()
      .|.nodeByLabelScan("b", "Person")
      .nodeByLabelScan("a", "Person")
      .build()
  }

  test("should plan FULL COLLECT expression with ORDER BY") {
    val plan = planner.plan(
      "MATCH (a:Person) RETURN COLLECT { MATCH (a)-[r:KNOWS]->(c) RETURN c ORDER BY c} AS x"
    ).stripProduceResults
    plan should equal(planner.subPlanBuilder()
      .rollUpApply("x", "c")
      .|.sort("c ASC")
      .|.expandAll("(a)-[r:KNOWS]->(c)")
      .|.argument("a")
      .nodeByLabelScan("a", "Person", IndexOrderNone)
      .build())
  }

  test("should plan FULL COLLECT expression with SKIP") {
    val plan = planner.plan(
      "MATCH (a:Person) RETURN COLLECT { MATCH (a)-[r:KNOWS]->(c) RETURN c SKIP 7} AS x"
    ).stripProduceResults
    plan should equal(planner.subPlanBuilder()
      .rollUpApply("x", "c")
      .|.skip(7L)
      .|.expandAll("(a)-[r:KNOWS]->(c)")
      .|.argument("a")
      .nodeByLabelScan("a", "Person", IndexOrderNone)
      .build())
  }

  test("should plan FULL COLLECT expression with LIMIT") {
    val plan = planner.plan(
      "MATCH (a:Person) RETURN COLLECT { MATCH (a)-[r:KNOWS]->(c) RETURN c LIMIT 42} AS x"
    ).stripProduceResults
    plan should equal(planner.subPlanBuilder()
      .rollUpApply("x", "c")
      .|.limit(42L)
      .|.expandAll("(a)-[r:KNOWS]->(c)")
      .|.argument("a")
      .nodeByLabelScan("a", "Person", IndexOrderNone)
      .build())
  }

  test("should plan FULL COLLECT expression with ORDER BY, SKIP and LIMIT") {
    val plan = planner.plan(
      "MATCH (a:Person) RETURN COLLECT { MATCH (a)-[r:KNOWS]->(c) RETURN c ORDER BY c SKIP 7 LIMIT 42 } AS x"
    ).stripProduceResults
    plan should equal(planner.subPlanBuilder()
      .rollUpApply("x", "c")
      .|.skip(7L)
      .|.top(
        Seq(Ascending(varFor("c"))),
        add(SignedDecimalIntegerLiteral("42")(pos), SignedDecimalIntegerLiteral("7")(pos))
      )
      .|.expandAll("(a)-[r:KNOWS]->(c)")
      .|.argument("a")
      .nodeByLabelScan("a", "Person", IndexOrderNone)
      .build())
  }

  test("should plan FULL COLLECT expression with DISTINCT") {
    val plan = planner.plan(
      "MATCH (a:Person) RETURN COLLECT { MATCH (a)-[r:KNOWS]->(c) RETURN DISTINCT c} AS x"
    ).stripProduceResults
    plan should equal(planner.subPlanBuilder()
      .rollUpApply("x", "c")
      .|.distinct("c AS c")
      .|.expandAll("(a)-[r:KNOWS]->(c)")
      .|.argument("a")
      .nodeByLabelScan("a", "Person", IndexOrderNone)
      .build())
  }

  private def reduceExpr(anonOffset: Int): ReduceExpression = reduce(
    varFor("sum"),
    literalInt(0),
    varFor("x"),
    varFor(s"anon_$anonOffset"),
    add(varFor("sum"), varFor("x"))
  )

  test("should consider variables introduced by outer list comprehensions when planning pattern predicates") {
    val plan = planner.plan(
      """MATCH (a:Person)-[:KNOWS]->(b:Person) WITH a, collect(b) AS friends RETURN a, [f IN friends WHERE (f)-[:WORKS_AT]->(:ComedyClub)] AS clowns"""
    ).stripProduceResults

    val nestedPlan = planner.subPlanBuilder()
      .filter("anon_2:ComedyClub")
      .expand("(f)-[anon_1:WORKS_AT]->(anon_2)")
      .argument("f")
      .build()

    val projectionExpression = listComprehension(
      varFor("f"),
      varFor("friends"),
      Some(
        NestedPlanExistsExpression(
          nestedPlan,
          s"EXISTS { MATCH (f)-[`anon_1`:WORKS_AT]->(`anon_2`)$NL  WHERE `anon_2`:ComedyClub }"
        )(pos)
      ),
      None
    )

    plan should (
      equal(
        planner.subPlanBuilder()
          .projection(project = Map("clowns" -> projectionExpression), discard = Set("friends"))
          .orderedAggregation(Seq("a AS a"), Seq("collect(b) AS friends"), Seq("a"))
          .filter("b:Person")
          .expandAll("(a)-[anon_0:KNOWS]->(b)")
          .nodeByLabelScan("a", "Person", IndexOrderAscending)
          .build()
      ) or equal(
        // TODO This plan is more expensive because of the aggregation (compared to ordered aggregation)
        //      but we never reach that cost comparison.
        planner.subPlanBuilder()
          .projection(project = Map("clowns" -> projectionExpression), discard = Set("friends"))
          .aggregation(Seq("a AS a"), Seq("collect(b) AS friends"))
          .filter("a:Person")
          .expandAll("(b)<-[anon_0:KNOWS]-(a)")
          .nodeByLabelScan("b", "Person", IndexOrderNone)
          .build()
      )
    )
  }

  test("should get the right scope for pattern comprehensions in ORDER BY") {
    // The important thing for this test is "RETURN u.id" instead of "RETURN u".
    // Like this the scoping is challenged to propagate `u` from the previous scope into the pattern expression
    val logicalPlan = planner.plan("MATCH (u:User) RETURN u.id ORDER BY size([(u)-[r:FOLLOWS]->(u2:User) | u2.id])")

    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("`u.id`")
        .projection(project = Seq("u.id AS `u.id`"), discard = Set("u", "anon_1", "size(anon_6)"))
        .sort("`size(anon_6)` ASC")
        .projection("size(anon_1) AS `size(anon_6)`")
        .rollUpApply("anon_1", "anon_0")
        .|.projection("u2.id AS anon_0")
        .|.filter("u2:User")
        .|.expandAll("(u)-[r:FOLLOWS]->(u2)")
        .|.argument("u")
        .nodeByLabelScan("u", "User", IndexOrderNone)
        .build()
    )
  }

  // Please look at the SemiApplyVsGetDegree benchmark.
  // GetDegree is faster than SemiApply.

  test("should build plans with GetDegree for a single pattern predicate") {
    val logicalPlan = planner.plan("MATCH (a) WHERE (a)-[:X]->() RETURN a")
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("a")
        .filterExpression(HasDegreeGreaterThan(
          varFor("a"),
          Some(RelTypeName("X")(pos)),
          SemanticDirection.OUTGOING,
          literalInt(0)
        )(pos))
        .allNodeScan("a")
        .build()
    )
  }

  test("should build plans with GetDegree for a single negated pattern predicate") {
    val logicalPlan = planner.plan("MATCH (a) WHERE NOT (a)-[:X]->() RETURN a")
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("a")
        .filterExpression(not(HasDegreeGreaterThan(
          varFor("a"),
          Some(RelTypeName("X")(pos)),
          SemanticDirection.OUTGOING,
          literalInt(0)
        )(pos)))
        .allNodeScan("a")
        .build()
    )
  }

  test("should build plans with GetDegree for a single pattern predicate with exists") {
    val logicalPlan = planner.plan("MATCH (a) WHERE exists((a)-[:X]->()) RETURN a")
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("a")
        .filterExpression(HasDegreeGreaterThan(
          varFor("a"),
          Some(RelTypeName("X")(pos)),
          SemanticDirection.OUTGOING,
          literalInt(0)
        )(pos))
        .allNodeScan("a")
        .build()
    )
  }

  test("should build plans with GetDegree for a single pattern predicate after a WITH") {
    val logicalPlan = planner.plan("MATCH (a) WITH a AS b WHERE EXISTS((b)-[:X]->()) RETURN b")
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("b")
        .filterExpression(HasDegreeGreaterThan(
          varFor("b"),
          Some(RelTypeName("X")(pos)),
          SemanticDirection.OUTGOING,
          literalInt(0)
        )(pos))
        .projection(project = Seq("a AS b"), discard = Set("a"))
        .allNodeScan("a")
        .build()
    )
  }

  test("should build plans with GetDegree for a single negated pattern predicate with exists") {
    val logicalPlan = planner.plan("MATCH (a) WHERE NOT exists((a)-[:X]->()) RETURN a")
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("a")
        .filterExpression(not(HasDegreeGreaterThan(
          varFor("a"),
          Some(RelTypeName("X")(pos)),
          SemanticDirection.OUTGOING,
          literalInt(0)
        )(pos)))
        .allNodeScan("a")
        .build()
    )
  }

  test("should build plans with GetDegree for a single negated pattern predicate after a WITH") {
    val logicalPlan = planner.plan("MATCH (a) WITH a AS b WHERE NOT exists((b)-[:X]->()) RETURN b")
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("b")
        .filterExpression(not(HasDegreeGreaterThan(
          varFor("b"),
          Some(RelTypeName("X")(pos)),
          SemanticDirection.OUTGOING,
          literalInt(0)
        )(pos)))
        .projection(project = Seq("a AS b"), discard = Set("a"))
        .allNodeScan("a")
        .build()
    )
  }

  test("should build plans with GetDegree for a single pattern predicate with 0 < COUNT") {
    val logicalPlan = planner.plan("MATCH (a) WHERE 0<COUNT{(a)-[:X]->()} RETURN a")
    logicalPlan should equal(
      new LogicalPlanBuilder()
        .produceResults("a")
        .filterExpression(HasDegreeGreaterThan(
          varFor("a"),
          Some(RelTypeName("X")(pos)),
          SemanticDirection.OUTGOING,
          literalInt(0)
        )(pos))
        .allNodeScan("a")
        .build()
    )
  }

  test("should build plans with GetDegree for a single pattern predicate with 0=COUNT") {
    val logicalPlan = planner.plan("MATCH (a) WHERE 0=COUNT{(a)-[:X]->()} RETURN a")
    logicalPlan should equal(
      new LogicalPlanBuilder()
        .produceResults("a")
        .filterExpression(not(HasDegreeGreaterThan(
          varFor("a"),
          Some(RelTypeName("X")(pos)),
          SemanticDirection.OUTGOING,
          literalInt(0)
        )(pos)))
        .allNodeScan("a")
        .build()
    )
  }

  test("should build plans with AntiSemiApply for a pattern predicate with 0=COUNT with WHERE inside") {
    val logicalPlan =
      planner.plan("MATCH (a) WHERE 0=COUNT{(a)-[:X]->() WHERE a.prop = 'c'} RETURN a")
    logicalPlan should equal(
      new LogicalPlanBuilder()
        .produceResults("a")
        .antiSemiApply()
        .|.expandAll("(a)-[anon_0:X]->(anon_1)")
        .|.filter("a.prop = 'c'")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test("should build plans with SemiApply for a pattern predicate with 0<COUNT with WHERE inside") {
    val logicalPlan =
      planner.plan("MATCH (a) WHERE 0<COUNT{(a)-[:X]->(b) WHERE b.prop = 'c'} RETURN a")
    logicalPlan should equal(
      new LogicalPlanBuilder()
        .produceResults("a")
        .semiApply()
        .|.filter("b.prop = 'c'")
        .|.expandAll("(a)-[anon_0:X]->(b)")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test("should build plans with SemiApply for a single pattern predicate with COUNT > 0 with Label on other node") {
    val logicalPlan = planner.plan("MATCH (a) WHERE COUNT{(a)-[:X]->(:Foo)}>0 RETURN a")
    logicalPlan should equal(
      new LogicalPlanBuilder()
        .produceResults("a")
        .semiApply()
        .|.filter("anon_1:Foo")
        .|.expandAll("(a)-[anon_0:X]->(anon_1)")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test(
    "should build plans with AntiSemiApply for a single pattern predicate with COUNT = 0 with Label on other node"
  ) {
    val logicalPlan = planner.plan("MATCH (a) WHERE COUNT{(a)-[:X]->(:Foo)}=0 RETURN a")
    logicalPlan should equal(
      new LogicalPlanBuilder()
        .produceResults("a")
        .antiSemiApply()
        .|.filter("anon_1:Foo")
        .|.expandAll("(a)-[anon_0:X]->(anon_1)")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test(
    "should preserve inner query predicates when planning a Simple EXISTS"
  ) {
    val logicalPlan = planner.plan("MATCH (a) WHERE EXISTS{(a)-[r]->(b WHERE b.prop > 5)-[q]-() } RETURN a")
    logicalPlan should equal(
      new LogicalPlanBuilder()
        .produceResults("a")
        .semiApply()
        .|.filter("not q = r")
        .|.expandAll("(b)-[q]-(anon_0)")
        .|.filter("b.prop > 5")
        .|.expandAll("(a)-[r]->(b)")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test(
    "should preserve inner query predicates when planning a Full EXISTS"
  ) {
    val logicalPlan =
      planner.plan("MATCH (a) WHERE EXISTS{ MATCH (a)-[r]->(b WHERE b.prop > 5)-[q]-() RETURN a } RETURN a")
    logicalPlan should equal(
      new LogicalPlanBuilder()
        .produceResults("a")
        .semiApply()
        .|.filter("not q = r")
        .|.expandAll("(b)-[q]-(anon_0)")
        .|.filter("b.prop > 5")
        .|.expandAll("(a)-[r]->(b)")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test("should build plans with GetDegree for a single pattern in a pattern comprehension, with 0 < size(pt)") {
    val logicalPlan =
      planner.plan("MATCH (a) WHERE 0 < size([pt = (a)-[:X]->() | pt]) RETURN a")
    logicalPlan should equal(
      new LogicalPlanBuilder()
        .produceResults("a")
        .filterExpression(HasDegreeGreaterThan(
          varFor("a"),
          Some(RelTypeName("X")(pos)),
          SemanticDirection.OUTGOING,
          literalInt(0)
        )(pos))
        .allNodeScan("a")
        .build()
    )
  }

  test("should build plans with GetDegree for a single negated pattern in a pattern comprehension, 0 < size(pt)") {
    val logicalPlan =
      planner.plan("MATCH (a) WHERE 0=size([pt = (a)-[:X]->() | pt]) RETURN a")
    logicalPlan should equal(
      new LogicalPlanBuilder()
        .produceResults("a")
        .filterExpression(not(HasDegreeGreaterThan(
          varFor("a"),
          Some(RelTypeName("X")(pos)),
          SemanticDirection.OUTGOING,
          literalInt(0)
        )(pos)))
        .allNodeScan("a")
        .build()
    )
  }

  test(
    "should build plans with SemiApply for a single pattern in a pattern comprehension, size(pt) > 0, with Label on other node"
  ) {
    val logicalPlan =
      planner.plan("MATCH (a) WHERE size([pt = (a)-[:X]->(:Foo) | pt]) > 0 RETURN a")
    logicalPlan should equal(
      new LogicalPlanBuilder()
        .produceResults("a")
        .semiApply()
        .|.filter("anon_1:Foo")
        .|.expandAll("(a)-[anon_0:X]->(anon_1)")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test(
    "should build plans with AntiSemiApply for a single negated pattern in a pattern comprehension, size(pt) = 0, with Label on other node"
  ) {
    val logicalPlan =
      planner.plan("MATCH (a) WHERE size([pt = (a)-[:X]->(:Foo) | pt]) = 0 RETURN a")
    logicalPlan should equal(
      new LogicalPlanBuilder()
        .produceResults("a")
        .antiSemiApply()
        .|.filter("anon_1:Foo")
        .|.expandAll("(a)-[anon_0:X]->(anon_1)")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test("should build plans with 2 SemiApplies for two pattern predicates with labels on the other node") {
    val logicalPlan = planner.plan("MATCH (a) WHERE (a)-[:X]->(:B) AND (a)-[:Y]->(:B) RETURN a")
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("a")
        .semiApply()
        .|.filter("anon_3:B")
        .|.expandAll("(a)-[anon_2:Y]->(anon_3)")
        .|.argument("a")
        .semiApply()
        .|.filter("anon_1:B")
        .|.expandAll("(a)-[anon_0:X]->(anon_1)")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test(
    "should build plans with SelectOrSemiApply for a pattern predicate with labels on the other node and an expression"
  ) {
    val logicalPlan = planner.plan("MATCH (a) WHERE (a)-[:X]->(:B) OR a.prop > 4 RETURN a")
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("a")
        .selectOrSemiApply("a.prop > 4")
        .|.filter("anon_1:B")
        .|.expandAll("(a)-[anon_0:X]->(anon_1)")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test(
    "should build plans with SelectOrSemiApply for a pattern predicate with labels on the other node and multiple expressions"
  ) {
    val logicalPlan =
      planner.plan("MATCH (a) WHERE a.prop2 = 9 OR (a)-[:X]->(:B) OR a.prop > 4 RETURN a")
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("a")
        .selectOrSemiApply("a.prop > 4 OR a.prop2 = 9")
        .|.filter("anon_1:B")
        .|.expandAll("(a)-[anon_0:X]->(anon_1)")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test(
    "should build plans with SelectOrAntiSemiApply for a single negated pattern predicate with labels on the other node and an expression"
  ) {
    val logicalPlan = planner.plan("MATCH (a) WHERE a.prop = 9 OR NOT (a)-[:X]->(:B) RETURN a")
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("a")
        .selectOrAntiSemiApply("a.prop = 9")
        .|.filter("anon_1:B")
        .|.expandAll("(a)-[anon_0:X]->(anon_1)")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test(
    "should build plans with LetSelectOrSemiApply and SelectOrAntiSemiApply for two pattern predicates with labels on the other node and expressions"
  ) {
    val logicalPlan = planner.plan("MATCH (a) WHERE a.prop = 9 OR (a)-[:Y]->(:B) OR NOT (a)-[:X]->(:B) RETURN a")

    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("a")
        .selectOrAntiSemiApply("anon_7")
        .|.filter("anon_3:B")
        .|.expandAll("(a)-[anon_2:X]->(anon_3)")
        .|.argument("a")
        .letSelectOrSemiApply(
          "anon_7",
          "a.prop = 9"
        ) // anon_7 is used to not go into the RHS of SelectOrAntiSemiApply if not needed
        .|.filter("anon_1:B")
        .|.expandAll("(a)-[anon_0:Y]->(anon_1)")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test(
    "should build plans with LetSemiApply and SelectOrAntiSemiApply for two pattern predicates with labels on the other node with one negation"
  ) {
    val logicalPlan = planner.plan("MATCH (a) WHERE (a)-[:Y]->(:B) OR NOT (a)-[:X]->(:B) RETURN a")

    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("a")
        .selectOrAntiSemiApply("anon_7")
        .|.filter("anon_3:B")
        .|.expandAll("(a)-[anon_2:X]->(anon_3)")
        .|.argument("a")
        .letSemiApply("anon_7") // anon_7 is used to not go into the RHS of SelectOrAntiSemiApply if not needed
        .|.filter("anon_1:B")
        .|.expandAll("(a)-[anon_0:Y]->(anon_1)")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test("should build plans with GetDegree for two negated pattern predicates") {
    val query = "MATCH (a) WHERE NOT (a)-[:Y]->() OR NOT (a)-[:X]->() RETURN a"
    val logicalPlan = planner.plan(query).stripProduceResults

    logicalPlan should equal(
      planner.subPlanBuilder()
        .filterExpression(ors(
          not(HasDegreeGreaterThan(
            varFor("a"),
            Some(RelTypeName("Y")(pos)),
            SemanticDirection.OUTGOING,
            literalInt(0)
          )(pos)),
          not(HasDegreeGreaterThan(
            varFor("a"),
            Some(RelTypeName("X")(pos)),
            SemanticDirection.OUTGOING,
            literalInt(0)
          )(pos))
        ))
        .allNodeScan("a")
        .build()
    )
  }

  test(
    "should build plans with LetAntiSemiApply and SelectOrAntiSemiApply for two negated pattern predicates with labels on the other node"
  ) {
    val query = "MATCH (a) WHERE NOT (a)-[:Y]->(:B) OR NOT (a)-[:X]->(:B) RETURN a"
    val logicalPlan = planner.plan(query).stripProduceResults

    logicalPlan should equal(
      planner.subPlanBuilder()
        .selectOrAntiSemiApply("anon_7")
        .|.filter("anon_3:B")
        .|.expandAll("(a)-[anon_2:X]->(anon_3)")
        .|.argument("a")
        .letAntiSemiApply("anon_7") // anon_7 is used to not go into the RHS of SelectOrAntiSemiApply if not needed
        .|.filter("anon_1:B")
        .|.expandAll("(a)-[anon_0:Y]->(anon_1)")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test("should build plans with SemiApply for a single pattern predicate with Label on other node") {
    val logicalPlan = planner.plan("MATCH (a) WHERE (a)-[:X]->(:Foo) RETURN a")
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("a")
        .semiApply()
        .|.filter("anon_1:Foo")
        .|.expandAll("(a)-[anon_0:X]->(anon_1)")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test("should build plans with AntiSemiApply for a single negated pattern predicate with Label on other node") {
    val logicalPlan = planner.plan("MATCH (a) WHERE NOT (a)-[:X]->(:Foo) RETURN a")
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("a")
        .antiSemiApply()
        .|.filter("anon_1:Foo")
        .|.expandAll("(a)-[anon_0:X]->(anon_1)")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test("should build plans with SemiApply for a single pattern predicate with exists with Label on other node") {
    val logicalPlan = planner.plan("MATCH (a) WHERE exists((a)-[:X]->(:Foo)) RETURN a")
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("a")
        .semiApply()
        .|.filter("anon_1:Foo")
        .|.expandAll("(a)-[anon_0:X]->(anon_1)")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test(
    "should build plans with SemiApply for a single pattern predicate with label after a WITH"
  ) {
    val logicalPlan = planner.plan("MATCH (a) WITH a as b WHERE exists((b)-[:X]->(:Foo)) RETURN b")
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("b")
        .semiApply()
        .|.filter("anon_1:Foo")
        .|.expandAll("(b)-[anon_0:X]->(anon_1)")
        .|.argument("b")
        .projection(project = Seq("a AS b"), discard = Set("a"))
        .allNodeScan("a")
        .build()
    )
  }

  test(
    "should build plans with SelectOrSemiApply for a single pattern predicate with label ORed with another predicate after a WITH"
  ) {
    val logicalPlan = planner.plan("MATCH (a) WITH a as b WHERE exists((b)-[:X]->(:Foo)) OR b.prop = 10 RETURN b")
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("b")
        .selectOrSemiApply("b.prop = 10")
        .|.filter("anon_1:Foo")
        .|.expandAll("(b)-[anon_0:X]->(anon_1)")
        .|.argument("b")
        .projection(project = Seq("a AS b"), discard = Set("a"))
        .allNodeScan("a")
        .build()
    )
  }

  test(
    "should build plans with AntiSemiApply for a single negated pattern predicate with exists with Label on other node"
  ) {
    val logicalPlan = planner.plan("MATCH (a) WHERE NOT exists((a)-[:X]->(:Foo)) RETURN a")
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("a")
        .antiSemiApply()
        .|.filter("anon_1:Foo")
        .|.expandAll("(a)-[anon_0:X]->(anon_1)")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test(
    "should build plans with AntiSemiApply for a single negated pattern predicate with a label after a WITH"
  ) {
    val logicalPlan = planner.plan("MATCH (a) WITH a as b WHERE NOT exists((b)-[:X]->(:Foo)) RETURN b")
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("b")
        .antiSemiApply()
        .|.filter("anon_1:Foo")
        .|.expandAll("(b)-[anon_0:X]->(anon_1)")
        .|.argument("b")
        .projection(project = Seq("a AS b"), discard = Set("a"))
        .allNodeScan("a")
        .build()
    )
  }

  test("should plan all predicates along with named varlength pattern") {
    val plan =
      planner.plan("MATCH p=(a)-[r*]->(b) WHERE all(n in nodes(p) WHERE n.prop = 1337) RETURN p").stripProduceResults

    val pathExpression = PathExpression(NodePathStep(
      varFor("a"),
      MultiRelationshipPathStep(varFor("r"), OUTGOING, Some(varFor("b")), NilPathStep()(pos))(pos)
    )(pos))(pos)

    plan should equal(
      planner.subPlanBuilder()
        .projection(project = Map("p" -> pathExpression), discard = Set("a", "b", "r"))
        .expand(
          "(a)-[r*1..]->(b)",
          expandMode = ExpandAll,
          projectedDir = OUTGOING,
          nodePredicates = Seq(Predicate("n", "n.prop = 1337"))
        )
        .allNodeScan("a")
        .build()
    )
  }

  test("should plan none predicates along with named varlength pattern") {
    val plan =
      planner.plan("MATCH p=(a)-[r*]->(b) WHERE none(n in nodes(p) WHERE n.prop = 1337) RETURN p").stripProduceResults

    val pathExpression = PathExpression(NodePathStep(
      varFor("a"),
      MultiRelationshipPathStep(varFor("r"), OUTGOING, Some(varFor("b")), NilPathStep()(pos))(pos)
    )(pos))(pos)

    plan should equal(
      planner.subPlanBuilder()
        .projection(project = Map("p" -> pathExpression), discard = Set("a", "b", "r"))
        .expand(
          "(a)-[r*1..]->(b)",
          expandMode = ExpandAll,
          projectedDir = OUTGOING,
          nodePredicates = Seq(Predicate("n", "NOT(n.prop = 1337)"))
        )
        .allNodeScan("a")
        .build()
    )
  }

  test("should solve pattern comprehensions as part of VarExpand") {
    val q =
      """
        |MATCH p= ( (b) -[:REL*0..]- (c) )
        |WHERE
        | ALL(n in nodes(p) where
        |   n.prop <= 1 < n.prop2
        |   AND coalesce(
        |     head( [ (n)<--(d) WHERE d.prop3 <= 1 < d.prop2 | d.prop4 = true ] ),
        |     head( [ (n)<--(e) WHERE e.prop3 <= 1 < e.prop2 | e.prop5 = '0'] ),
        |     true)
        | )
        | AND ALL(r in relationships(p) WHERE r.prop <= 1 < r.prop2)
        |RETURN c
      """.stripMargin

    planner.plan(q) // Should not fail
    // The plan that solves the predicates as part of VarExpand is not chosen, but considered, thus we cannot assert on _that_ plan here.
    // Nevertheless the assertion LogicalPlanProducer.assertNoBadExpressionsExists should never fail, even for plans that do not get chosen.
  }

  test("should not use RollupApply for PatternComprehensions in coalesce") {
    val q =
      """
        |MATCH (a)
        |WHERE coalesce(
        |     head( [ (a)<--(b) | b.prop4 = true ] ),
        |     head( [ (a)<--(c) | c.prop5 = '0'] ),
        |     true)
        |RETURN a
      """.stripMargin

    val plan = planner.plan(q)
    plan.folder.treeExists({
      case _: RollUpApply => true
    }) should be(false)
  }

  test("should not use RollupApply for PatternComprehensions in coalesce v2") {
    val q =
      """
        |MATCH (a)
        |WHERE NOT isEmpty(
        |     coalesce(
        |       [1, 2, 3],
        |       [(a)<--(c) | c.prop5 = '0'],
        |       [true]
        |     ))
        |RETURN a
      """.stripMargin

    val plan = planner.plan(q)
    plan.folder.treeExists({
      case _: RollUpApply => true
    }) should be(false)
  }

  test("should solve pattern comprehension for NodeByIdSeek") {
    val q =
      """
        |MATCH (n)
        |WHERE id(n) = reduce(sum=0, x IN [(a)-->(b) | b.age] | sum + x)
        |RETURN n
      """.stripMargin

    val plan = planner.plan(q).stripProduceResults

    plan should equal(
      planner.subPlanBuilder()
        .apply()
        .|.nodeByIdSeek("n", Set("anon_2"), "reduce(sum = 0, x IN anon_2 | sum + x)")
        .rollUpApply("anon_2", "anon_1")
        .|.projection("b.age AS anon_1")
        .|.allRelationshipsScan("(a)-[anon_0]->(b)")
        .argument()
        .build()
    )
  }

  test("should solve pattern comprehension for DirectedRelationshipByIdSeek") {
    val q =
      """
        |MATCH ()-[r]->()
        |WHERE id(r) = reduce(sum=0, x IN [(a)-->(b) | b.age] | sum + x)
        |RETURN r
      """.stripMargin

    val plan = planner.plan(q).stripProduceResults

    plan should equal(
      planner.subPlanBuilder()
        .apply()
        .|.directedRelationshipByIdSeekExpr("r", "anon_0", "anon_1", Set("anon_4"), reduceExpr(4))
        .rollUpApply("anon_4", "anon_3")
        .|.projection("b.age AS anon_3")
        .|.allRelationshipsScan("(a)-[anon_2]->(b)")
        .argument()
        .build()
    )
  }

  test("should solve pattern comprehension for UndirectedRelationshipByIdSeek") {
    val q =
      """
        |MATCH ()-[r]-()
        |WHERE id(r) = reduce(sum=0, x IN [(a)-->(b) | b.age] | sum + x)
        |RETURN r
      """.stripMargin

    val plan = planner.plan(q).stripProduceResults

    plan should equal(
      planner.subPlanBuilder()
        .apply()
        .|.undirectedRelationshipByIdSeekExpr("r", "anon_0", "anon_1", Set("anon_4"), reduceExpr(4))
        .rollUpApply("anon_4", "anon_3")
        .|.projection("b.age AS anon_3")
        .|.allRelationshipsScan("(a)-[anon_2]->(b)")
        .argument()
        .build()
    )
  }

  test("should solve pattern comprehension for NodeIndexSeek") { // change labels so that one is unique and the other one nonunique
    val q =
      """
        |MATCH (n:Label)
        |WHERE n.prop = reduce(sum=0, x IN [(a)-->(b) | b.age] | sum + x)
        |RETURN n
      """.stripMargin

    val plan = planner.plan(q).stripProduceResults

    plan should equal(
      planner.subPlanBuilder()
        .apply()
        .|.nodeIndexOperator("n:Label(prop = ???)", argumentIds = Set("anon_2"), paramExpr = Seq(reduceExpr(2)))
        .rollUpApply("anon_2", "anon_1")
        .|.projection("b.age AS anon_1")
        .|.allRelationshipsScan("(a)-[anon_0]->(b)")
        .argument()
        .build()
    )
  }

  test("should solve pattern comprehension for relationship index seek") {
    val q =
      """
        |MATCH ()-[r:REL]->()
        |WHERE r.prop = reduce(sum=0, x IN [(a)-->(b) | b.age] | sum + x)
        |RETURN r
      """.stripMargin

    val plan = planner.plan(q).stripProduceResults

    plan should equal(
      planner.subPlanBuilder()
        .apply()
        .|.relationshipIndexOperator(
          "(anon_0)-[r:REL(prop = ???)]->(anon_1)",
          argumentIds = Set("anon_4"),
          paramExpr = Seq(reduceExpr(4))
        )
        .rollUpApply("anon_4", "anon_3")
        .|.projection("b.age AS anon_3")
        .|.allRelationshipsScan("(a)-[anon_2]->(b)")
        .argument()
        .build()
    )
  }

  test("should solve pattern comprehension for NodeUniqueIndexSeek") {
    val q =
      """
        |MATCH (n:UniqueLabel)
        |WHERE n.prop = reduce(sum=0, x IN [(a)-->(b) | b.age] | sum + x)
        |RETURN n
      """.stripMargin

    val plan = planner.plan(q).stripProduceResults

    plan should equal(
      planner.subPlanBuilder()
        .apply()
        .|.nodeIndexOperator(
          "n:UniqueLabel(prop = ???)",
          unique = true,
          argumentIds = Set("anon_2"),
          paramExpr = Seq(reduceExpr(2))
        )
        .rollUpApply("anon_2", "anon_1")
        .|.projection("b.age AS anon_1")
        .|.allRelationshipsScan("(a)-[anon_0]->(b)")
        .argument()
        .build()
    )
  }

  test("should solve pattern comprehension for NodeIndexContainsScan") {
    val q =
      """
        |MATCH (n:TextLabel)
        |WHERE n.prop CONTAINS toString(reduce(sum=0, x IN [(a)-->(b) | b.age] | sum + x))
        |RETURN n
      """.stripMargin

    val plan = planner.plan(q).stripProduceResults
    val toStrings = function("toString", reduceExpr(2))

    plan should equal(
      planner.subPlanBuilder()
        .apply()
        .|.nodeIndexOperator(
          "n:TextLabel(prop CONTAINS ???)",
          indexType = IndexType.TEXT,
          paramExpr = Seq(toStrings),
          argumentIds = Set("anon_2")
        )
        .rollUpApply("anon_2", "anon_1")
        .|.projection("b.age AS anon_1")
        .|.allRelationshipsScan("(a)-[anon_0]->(b)")
        .argument()
        .build()
    )
  }

  test("should solve pattern comprehension for DirectedRelationshipIndexContainsScan") {
    val q =
      """
        |MATCH ()-[r:TextRel]->()
        |WHERE r.prop CONTAINS toString(reduce(sum=0, x IN [(a)-->(b) | b.age] | sum + x))
        |RETURN r
      """.stripMargin

    val plan = planner.plan(q).stripProduceResults
    val toStrings = function("toString", reduceExpr(4))

    plan should equal(
      planner.subPlanBuilder()
        .apply()
        .|.relationshipIndexOperator(
          "(anon_0)-[r:TextRel(prop CONTAINS ???)]->(anon_1)",
          argumentIds = Set("anon_4"),
          indexType = IndexType.TEXT,
          paramExpr = Seq(toStrings)
        )
        .rollUpApply("anon_4", "anon_3")
        .|.projection("b.age AS anon_3")
        .|.allRelationshipsScan("(a)-[anon_2]->(b)")
        .argument()
        .build()
    )
  }

  test("should solve pattern comprehension for UndirectedRelationshipIndexEndsWithScan") {
    val q =
      """
        |MATCH ()-[r:TextRel]-()
        |WHERE r.prop ENDS WITH toString(reduce(sum=0, x IN [(a)-->(b) | b.age] | sum + x))
        |RETURN r
      """.stripMargin

    val plan = planner.plan(q).stripProduceResults
    val toStrings = function("toString", reduceExpr(4))

    plan should equal(
      planner.subPlanBuilder()
        .apply()
        .|.relationshipIndexOperator(
          "(anon_0)-[r:TextRel(prop ENDS WITH ???)]-(anon_1)",
          argumentIds = Set("anon_4"),
          indexType = IndexType.TEXT,
          paramExpr = Seq(toStrings)
        )
        .rollUpApply("anon_4", "anon_3")
        .|.projection("b.age AS anon_3")
        .|.allRelationshipsScan("(a)-[anon_2]->(b)")
        .argument()
        .build()
    )
  }

  test("should solve pattern comprehension for NodeIndexEndsWithScan") {
    val q =
      """
        |MATCH (n:TextLabel)
        |WHERE n.prop ENDS WITH toString(reduce(sum=0, x IN [(a)-->(b) | b.age] | sum + x))
        |RETURN n
      """.stripMargin

    val plan = planner.plan(q).stripProduceResults
    val toStrings = function("toString", reduceExpr(2))

    plan should equal(
      planner.subPlanBuilder()
        .apply()
        .|.nodeIndexOperator(
          "n:TextLabel(prop ENDS WITH ???)",
          indexType = IndexType.TEXT,
          paramExpr = Seq(toStrings),
          argumentIds = Set("anon_2")
        )
        .rollUpApply("anon_2", "anon_1")
        .|.projection("b.age AS anon_1")
        .|.allRelationshipsScan("(a)-[anon_0]->(b)")
        .argument()
        .build()
    )
  }

  test("should solve pattern comprehension for Selection") {
    val q =
      """
        |MATCH (n)
        |WHERE n.prop = reduce(sum=0, x IN [(a)-->(b) | b.age] | sum + x)
        |RETURN n
      """.stripMargin

    val plan = planner.plan(q).stripProduceResults

    plan should equal(
      planner.subPlanBuilder()
        .filter("n.prop = reduce(sum = 0, x IN anon_2 | sum + x)")
        .rollUpApply("anon_2", "anon_1")
        .|.projection("b.age AS anon_1")
        .|.allRelationshipsScan("(a)-[anon_0]->(b)")
        .allNodeScan("n")
        .build()
    )
  }

  test("should solve pattern comprehension for Horizon Selection") {
    val q =
      """
        |MATCH (n)
        |WITH n, 1 AS one
        |WHERE n.prop = reduce(sum=0, x IN [(a)-->(b) | b.age] | sum + x)
        |RETURN n
      """.stripMargin

    val plan = planner.plan(q).stripProduceResults

    plan should equal(
      planner.subPlanBuilder()
        .filter("n.prop = reduce(sum = 0, x IN anon_2 | sum + x)")
        .rollUpApply("anon_2", "anon_1")
        .|.projection("b.age AS anon_1")
        .|.allRelationshipsScan("(a)-[anon_0]->(b)")
        .projection("1 AS one")
        .allNodeScan("n")
        .build()
    )
  }

  test("should solve pattern comprehension for SelectOrAntiSemiApply") {
    val q =
      """
        |MATCH (n)
        |WHERE NOT (n)-[:R]->(:M) OR n.prop = reduce(sum=0, x IN [(a)-->(b) | b.age] | sum + x)
        |RETURN n
      """.stripMargin

    val plan = planner.plan(q).stripProduceResults

    plan should equal(
      planner.subPlanBuilder()
        .selectOrAntiSemiApply("n.prop = reduce(sum = 0, x IN anon_5 | sum + x)")
        .|.filter("anon_1:M")
        .|.expandAll("(n)-[anon_0:R]->(anon_1)")
        .|.argument("n")
        .rollUpApply("anon_5", "anon_4")
        .|.projection("b.age AS anon_4")
        .|.allRelationshipsScan("(a)-[anon_2]->(b)")
        .allNodeScan("n")
        .build()
    )
  }

  test("should solve pattern comprehension for LetSelectOrAntiSemiApply") {
    val q =
      """
        |MATCH (n)
        |WHERE NOT (n)-[:R]->(:M) OR NOT (n)-[:Q]->(:O) OR n.prop = reduce(sum=0, x IN [(a)-->(b) | b.age] | sum + x)
        |RETURN n
      """.stripMargin

    val plan = planner.plan(q).stripProduceResults

    plan should equal(
      planner.subPlanBuilder()
        .selectOrAntiSemiApply("anon_10")
        .|.filter("anon_3:O")
        .|.expandAll("(n)-[anon_2:Q]->(anon_3)")
        .|.argument("n")
        .letSelectOrAntiSemiApply("anon_10", "n.prop = reduce(sum = 0, x IN anon_8 | sum + x)")
        .|.filter("anon_1:M")
        .|.expandAll("(n)-[anon_0:R]->(anon_1)")
        .|.argument("n")
        .rollUpApply("anon_8", "anon_7")
        .|.projection("b.age AS anon_7")
        .|.allRelationshipsScan("(a)-[anon_4]->(b)")
        .allNodeScan("n")
        .build()
    )
  }

  test("should solve pattern comprehension for SelectOrSemiApply") {
    val q =
      """
        |MATCH (n)
        |WHERE (n)-[:R]->(:M) OR n.prop = reduce(sum=0, x IN [(a)-->(b) | b.age] | sum + x)
        |RETURN n
      """.stripMargin

    val plan = planner.plan(q).stripProduceResults

    plan should equal(
      planner.subPlanBuilder()
        .selectOrSemiApply("n.prop = reduce(sum = 0, x IN anon_5 | sum + x)")
        .|.filter("anon_1:M")
        .|.expandAll("(n)-[anon_0:R]->(anon_1)")
        .|.argument("n")
        .rollUpApply("anon_5", "anon_4")
        .|.projection("b.age AS anon_4")
        .|.allRelationshipsScan("(a)-[anon_2]->(b)")
        .allNodeScan("n")
        .build()
    )
  }

  test("should solve pattern comprehension for LetSelectOrSemiApply") {
    val q =
      """
        |MATCH (n)
        |WHERE (n)-[:R]->(:M) OR (n)-[:Q]->(:O) OR n.prop = reduce(sum=0, x IN [(a)-->(b) | b.age] | sum + x)
        |RETURN n
      """.stripMargin

    val plan = planner.plan(q).stripProduceResults

    plan should equal(
      planner.subPlanBuilder()
        .selectOrSemiApply("anon_10")
        .|.filter("anon_3:O")
        .|.expandAll("(n)-[anon_2:Q]->(anon_3)")
        .|.argument("n")
        .letSelectOrSemiApply("anon_10", "n.prop = reduce(sum = 0, x IN anon_8 | sum + x)")
        .|.filter("anon_1:M")
        .|.expandAll("(n)-[anon_0:R]->(anon_1)")
        .|.argument("n")
        .rollUpApply("anon_8", "anon_7")
        .|.projection("b.age AS anon_7")
        .|.allRelationshipsScan("(a)-[anon_4]->(b)")
        .allNodeScan("n")
        .build()
    )
  }

  test("should solve and name pattern comprehensions for Projection") {
    val q =
      """
        |MATCH (n)
        |RETURN [(n)-->(b) | b.age] AS ages
      """.stripMargin

    val plan = planner.plan(q).stripProduceResults
    plan should equal(
      planner.subPlanBuilder()
        .rollUpApply("ages", "anon_1")
        .|.projection("b.age AS anon_1")
        .|.expandAll("(n)-[anon_0]->(b)")
        .|.argument("n")
        .allNodeScan("n")
        .build()
    )
  }

  test("should solve and name nested pattern comprehensions for Projection") {
    val q =
      """
        |MATCH (n)
        |RETURN [1, [(n)-->(b) | b.age], 2] AS ages
      """.stripMargin

    val plan = planner.plan(q).stripProduceResults
    plan should equal(
      planner.subPlanBuilder()
        .projection(project = Seq("[1, anon_2, 2] AS ages"), discard = Set("n", "anon_2"))
        .rollUpApply("anon_2", "anon_1")
        .|.projection("b.age AS anon_1")
        .|.expandAll("(n)-[anon_0]->(b)")
        .|.argument("n")
        .allNodeScan("n")
        .build()
    )
  }

  test("should solve and name pattern comprehensions nested in pattern comprehensions for Projection") {
    val q =
      """
        |MATCH (n)
        |RETURN [(n)-->(b) | [(b)-->(c) | c.age]] AS ages
      """.stripMargin

    val plan = planner.plan(q).stripProduceResults
    plan should equal(
      planner.subPlanBuilder()
        .rollUpApply("ages", "anon_2")
        .|.rollUpApply("anon_2", "anon_4")
        .|.|.projection("c.age AS anon_4")
        .|.|.expandAll("(b)-[anon_1]->(c)")
        .|.|.argument("b")
        .|.expandAll("(n)-[anon_0]->(b)")
        .|.argument("n")
        .allNodeScan("n")
        .build()
    )
  }

  test("should solve and name pattern comprehensions with named paths for Projection") {
    val q =
      """
        |MATCH (n)
        |RETURN [p = (n)-->(b) | p] AS ages
      """.stripMargin

    val plan = planner.plan(q).stripProduceResults
    plan should equal(
      planner.subPlanBuilder()
        .rollUpApply("ages", "anon_1")
        .|.projection(Map("anon_1" -> PathExpression(
          NodePathStep(
            varFor("n"),
            SingleRelationshipPathStep(varFor("anon_0"), OUTGOING, Some(varFor("b")), NilPathStep()(pos))(pos)
          )(pos)
        )(pos)))
        .|.expandAll("(n)-[anon_0]->(b)")
        .|.argument("n")
        .allNodeScan("n")
        .build()
    )
  }

  test("should solve and name pattern comprehensions for Aggregation, grouping expression") {
    val q =
      """
        |MATCH (n)
        |RETURN [(n)-->(b) | b.age] AS ages, sum(n.foo)
      """.stripMargin

    val plan = planner.plan(q).stripProduceResults

    plan should equal(
      planner.subPlanBuilder()
        .aggregation(Seq("ages AS ages"), Seq("sum(n.foo) AS `sum(n.foo)`"))
        .rollUpApply("ages", "anon_1")
        .|.projection("b.age AS anon_1")
        .|.expandAll("(n)-[anon_0]->(b)")
        .|.argument("n")
        .allNodeScan("n")
        .build()
    )
  }

  test("should solve pattern comprehensions for Aggregation, aggregation expression") {
    val q =
      """
        |MATCH (n)
        |RETURN collect([(n)-->(b) | b.age]) AS ages, n.foo
      """.stripMargin

    val plan = planner.plan(q).stripProduceResults

    plan should equal(planner.subPlanBuilder()
      .aggregation(Seq("n.foo AS `n.foo`"), Seq("collect(anon_2) AS ages"))
      .rollUpApply("anon_2", "anon_1")
      .|.projection("b.age AS anon_1")
      .|.expandAll("(n)-[anon_0]->(b)")
      .|.argument("n")
      .allNodeScan("n")
      .build())
  }

  test("should solve and name pattern comprehensions for Distinct") {
    val q =
      """
        |MATCH (n)
        |RETURN DISTINCT [(n)-->(b) | b.age] AS ages
      """.stripMargin

    val plan = planner.plan(q).stripProduceResults

    plan should equal(planner.subPlanBuilder()
      .distinct("ages AS ages")
      .rollUpApply("ages", "anon_1")
      .|.projection("b.age AS anon_1")
      .|.expandAll("(n)-[anon_0]->(b)")
      .|.argument("n")
      .allNodeScan("n")
      .build())
  }

  test("should solve pattern comprehensions for LoadCSV") {
    val q =
      """
        |LOAD CSV FROM toString(reduce(sum=0, x IN [(a)-->(b) | b.age] | sum + x)) AS foo RETURN foo
      """.stripMargin

    val plan = planner.plan(q).stripProduceResults

    plan should equal(planner.subPlanBuilder()
      .loadCSV("toString(reduce(sum = 0, x IN anon_2 | sum + x))", "foo", NoHeaders, None)
      .rollUpApply("anon_2", "anon_1")
      .|.projection("b.age AS anon_1")
      .|.allRelationshipsScan("(a)-[anon_0]->(b)")
      .argument()
      .build())
  }

  test("should solve pattern comprehensions for Unwind") {
    val q =
      """
        |UNWIND [reduce(sum=0, x IN [(a)-->(b) | b.age] | sum + x)] AS foo RETURN foo
      """.stripMargin

    val plan = planner.plan(q).stripProduceResults

    plan should equal(planner.subPlanBuilder()
      .unwind("[reduce(sum = 0, x IN anon_2 | sum + x)] AS foo")
      .rollUpApply("anon_2", "anon_1")
      .|.projection("b.age AS anon_1")
      .|.allRelationshipsScan("(a)-[anon_0]->(b)")
      .argument()
      .build())
  }

  test("should solve pattern comprehensions for ShortestPath") {
    val q =
      """
        |MATCH p=shortestPath((n)-[r*..6]-(n2)) WHERE NONE(n in nodes(p) WHERE n.foo = reduce(sum=0, x IN [(a)-->(b) | b.age] | sum + x)) RETURN n, n2
      """.stripMargin

    val nestedPlan = planner.subPlanBuilder()
      .projection("b.age AS anon_3")
      .allRelationshipsScan("(a)-[anon_0]->(b)")
      .build()
    val nestedCollection =
      NestedPlanCollectExpression(nestedPlan, varFor("anon_3"), "[(a)-[`anon_0`]->(b) | b.age]")(pos)
    val reduceExprWithNestedPlan = reduce(
      varFor("sum", pos),
      literalInt(0, pos),
      varFor("x", pos),
      nestedCollection,
      add(varFor("sum", pos), varFor("x", pos))
    )

    val nodePred =
      VariablePredicate(varFor("n", pos), Not(equals(prop("n", "foo", pos), reduceExprWithNestedPlan))(pos))

    val plan = planner.plan(q).stripProduceResults

    plan should equal(planner.subPlanBuilder()
      .shortestPathExpr("(n)-[r*1..6]-(n2)", pathName = Some("p"), nodePredicates = Seq(nodePred))
      .cartesianProduct()
      .|.allNodeScan("n2")
      .allNodeScan("n")
      .build())
  }

  test("should solve pattern comprehensions for Create") {
    val q =
      """
        |CREATE (n {foo: reduce(sum=0, x IN [(a)-->(b) | b.age] | sum + x)}) RETURN n
      """.stripMargin

    val plan = planner.plan(q).stripProduceResults

    plan should equal(planner.subPlanBuilder()
      .create(createNodeWithProperties("n", Seq(), "{foo: reduce(sum = 0, x IN anon_2 | sum + x)}"))
      .rollUpApply("anon_2", "anon_1")
      .|.projection("b.age AS anon_1")
      .|.allRelationshipsScan("(a)-[anon_0]->(b)")
      .argument()
      .build())
  }

  test("should solve pattern comprehensions for MERGE node") {
    val q =
      """
        |MERGE (n {foo: reduce(sum=0, x IN [(a)-->(b) | b.age] | sum + x)}) RETURN n
      """.stripMargin

    val plan = planner.plan(q).stripProduceResults

    plan should equal(planner.subPlanBuilder()
      .merge(Seq(createNodeWithProperties("n", Seq(), "{foo: reduce(sum = 0, x IN anon_2 | sum + x)}")))
      .filter("n.foo = reduce(sum = 0, x IN anon_2 | sum + x)")
      .rollUpApply("anon_2", "anon_1")
      .|.projection("b.age AS anon_1")
      .|.allRelationshipsScan("(a)-[anon_0]->(b)")
      .allNodeScan("n")
      .build())
  }

  test("should solve pattern comprehensions for MERGE relationship") {
    val q =
      """
        |MERGE ()-[r:R {foo: reduce(sum=0, x IN [(a)-->(b) | b.age] | sum + x)}]->() RETURN r
      """.stripMargin

    val plan = planner.plan(q).stripProduceResults

    plan should equal(planner.subPlanBuilder()
      .merge(
        Seq(createNode("anon_0"), createNode("anon_2")),
        Seq(createRelationship(
          "r",
          "anon_0",
          "R",
          "anon_2",
          OUTGOING,
          Some("{foo: reduce(sum = 0, x IN anon_4 | sum + x)}")
        ))
      )
      .filter("r.foo = reduce(sum = 0, x IN anon_4 | sum + x)")
      .rollUpApply("anon_4", "anon_3")
      .|.projection("b.age AS anon_3")
      .|.allRelationshipsScan("(a)-[anon_1]->(b)")
      .relationshipTypeScan("(anon_0)-[r:R]->(anon_2)", IndexOrderNone)
      .build())
  }

  test("should solve pattern comprehensions for DeleteNode") {
    val q =
      """
        |MATCH (n)
        |WITH [n] AS nodes
        |DELETE nodes[reduce(sum=0, x IN [(a)-->(b) | b.age] | sum + x)]
      """.stripMargin

    val nestedPlan = planner.subPlanBuilder()
      .projection("b.age AS anon_1")
      .allRelationshipsScan("(a)-[anon_0]->(b)")
      .build()
    val nestedCollection =
      NestedPlanCollectExpression(nestedPlan, varFor("anon_1"), "[(a)-[`anon_0`]->(b) | b.age]")(pos)
    val reduceExprWithNestedPlan = reduce(
      varFor("sum", pos),
      literalInt(0, pos),
      varFor("x", pos),
      nestedCollection,
      add(varFor("sum", pos), varFor("x", pos))
    )
    val expr = ContainerIndex(Variable("nodes")(pos), reduceExprWithNestedPlan)(pos)
    val plan = planner.plan(q).stripProduceResults

    plan should equal(planner.subPlanBuilder()
      .emptyResult()
      .deleteNode(expr)
      .eager(ListSet(
        EagernessReason.ReadDeleteConflict("nodes"),
        EagernessReason.ReadDeleteConflict("a"),
        EagernessReason.ReadDeleteConflict("b")
      ))
      .projection(project = Seq("[n] AS nodes"), discard = Set("n"))
      .eager(ListSet(EagernessReason.ReadDeleteConflict("n")))
      .allNodeScan("n")
      .build())
  }

  test("should solve pattern comprehensions for DeleteRelationship") {
    val q =
      """
        |MATCH ()-[r]->()
        |WITH [r] AS rels
        |DELETE rels[reduce(sum=0, x IN [(a)-->(b) | b.age] | sum + x)]
      """.stripMargin

    val nestedPlan = planner.subPlanBuilder()
      .projection("b.age AS anon_3")
      .allRelationshipsScan("(a)-[anon_2]->(b)")
      .build()
    val nestedCollection =
      NestedPlanCollectExpression(nestedPlan, varFor("anon_3"), "[(a)-[`anon_2`]->(b) | b.age]")(pos)
    val reduceExprWithNestedPlan = reduce(
      varFor("sum", pos),
      literalInt(0, pos),
      varFor("x", pos),
      nestedCollection,
      add(varFor("sum", pos), varFor("x", pos))
    )
    val expr = ContainerIndex(Variable("rels")(pos), reduceExprWithNestedPlan)(pos)
    val plan = planner.plan(q).stripProduceResults

    plan should equal(planner.subPlanBuilder()
      .emptyResult()
      .deleteRelationship(expr)
      .eager(ListSet(
        EagernessReason.ReadDeleteConflict("rels"),
        EagernessReason.ReadDeleteConflict("a"),
        EagernessReason.ReadDeleteConflict("b")
      ))
      .projection(project = Seq("[r] AS rels"), discard = Set("r", "anon_0", "anon_1"))
      .eager(ListSet(
        EagernessReason.ReadDeleteConflict("anon_0"),
        EagernessReason.ReadDeleteConflict("anon_1")
      ))
      .allRelationshipsScan("(anon_0)-[r]->(anon_1)")
      .build())
  }

  test("should solve pattern comprehensions for DeleteExpression") {
    val q =
      """
        |MATCH ()-[r]->()
        |WITH {rel: r} AS rels
        |DELETE rels[toString(reduce(sum=0, x IN [(a)-->(b) | b.age] | sum + x))]
      """.stripMargin

    val nestedPlan = planner.subPlanBuilder()
      .projection("b.age AS anon_3")
      .allRelationshipsScan("(a)-[anon_2]->(b)")
      .build()
    val nestedCollection =
      NestedPlanCollectExpression(nestedPlan, varFor("anon_3"), "[(a)-[`anon_2`]->(b) | b.age]")(pos)
    val reduceExprWithNestedPlan = reduce(
      varFor("sum", pos),
      literalInt(0, pos),
      varFor("x", pos),
      nestedCollection,
      add(varFor("sum", pos), varFor("x", pos))
    )
    val toStrings = function("toString", reduceExprWithNestedPlan)
    val expr = containerIndex(varFor("rels", pos), toStrings)
    val plan = planner.plan(q).stripProduceResults

    plan should equal(planner.subPlanBuilder()
      .emptyResult()
      .deleteExpression(expr)
      .eager(ListSet(
        EagernessReason.ReadDeleteConflict("rels"),
        EagernessReason.ReadDeleteConflict("a"),
        EagernessReason.ReadDeleteConflict("b")
      ))
      .projection(project = Seq("{rel: r} AS rels"), discard = Set("r", "anon_0", "anon_1"))
      .eager(ListSet(
        EagernessReason.ReadDeleteConflict("anon_0"),
        EagernessReason.ReadDeleteConflict("anon_1")
      ))
      .allRelationshipsScan("(anon_0)-[r]->(anon_1)")
      .build())
  }

  test("should solve pattern comprehensions for SetNodeProperty") {
    val q =
      """
        |MATCH (n) SET n.foo = reduce(sum=0, x IN [(a)-->(b) | b.age] | sum + x) RETURN n
      """.stripMargin

    val plan = planner.plan(q).stripProduceResults

    plan should equal(planner.subPlanBuilder()
      .eager(ListSet(EagernessReason.Unknown))
      .setNodeProperty("n", "foo", "reduce(sum = 0, x IN anon_2 | sum + x)")
      .rollUpApply("anon_2", "anon_1")
      .|.projection("b.age AS anon_1")
      .|.allRelationshipsScan("(a)-[anon_0]->(b)")
      .allNodeScan("n")
      .build())
  }

  test("should solve pattern comprehensions for SetNodePropertiesFromMap") {
    val q =
      """
        |MATCH (n) SET n = {foo: reduce(sum=0, x IN [(a)-->(b) | b.age] | sum + x)} RETURN n
      """.stripMargin

    val plan = planner.plan(q).stripProduceResults

    plan should equal(planner.subPlanBuilder()
      .eager(ListSet(EagernessReason.Unknown))
      .setNodePropertiesFromMap("n", "{foo: reduce(sum = 0, x IN anon_2 | sum + x)}", removeOtherProps = true)
      .rollUpApply("anon_2", "anon_1")
      .|.projection("b.age AS anon_1")
      .|.allRelationshipsScan("(a)-[anon_0]->(b)")
      .allNodeScan("n")
      .build())
  }

  test("should solve pattern comprehensions for SetRelationshipProperty") {
    val q =
      """
        |MATCH ()-[r]->() SET r.foo = reduce(sum=0, x IN [(a)-->(b) | b.age] | sum + x) RETURN r
      """.stripMargin

    val plan = planner.plan(q).stripProduceResults

    plan should equal(planner.subPlanBuilder()
      .eager(ListSet(EagernessReason.Unknown))
      .setRelationshipProperty("r", "foo", "reduce(sum = 0, x IN anon_4 | sum + x)")
      .rollUpApply("anon_4", "anon_3")
      .|.projection("b.age AS anon_3")
      .|.allRelationshipsScan("(a)-[anon_2]->(b)")
      .allRelationshipsScan("(anon_0)-[r]->(anon_1)")
      .build())
  }

  test("should solve pattern comprehensions for SetRelationshipPropertiesFromMap") {
    val q =
      """
        |MATCH ()-[r]->() SET r = {foo: reduce(sum=0, x IN [(a)-->(b) | b.age] | sum + x)} RETURN r
      """.stripMargin

    val plan = planner.plan(q).stripProduceResults

    plan should equal(planner.subPlanBuilder()
      .eager(ListSet(EagernessReason.Unknown))
      .setRelationshipPropertiesFromMap("r", "{foo: reduce(sum = 0, x IN anon_4 | sum + x)}", true)
      .rollUpApply("anon_4", "anon_3")
      .|.projection("b.age AS anon_3")
      .|.allRelationshipsScan("(a)-[anon_2]->(b)")
      .allRelationshipsScan("(anon_0)-[r]->(anon_1)")
      .build())
  }

  test("should solve pattern comprehensions for SetProperty") {
    val q =
      """
        |SET $param.foo = reduce(sum=0, x IN [(a)-->(b) | b.age] | sum + x)
      """.stripMargin

    val plan = planner.plan(q).stripProduceResults

    plan should equal(planner.subPlanBuilder()
      .emptyResult()
      .setProperty("$param", "foo", "reduce(sum = 0, x IN anon_2 | sum + x)")
      .rollUpApply("anon_2", "anon_1")
      .|.projection("b.age AS anon_1")
      .|.allRelationshipsScan("(a)-[anon_0]->(b)")
      .argument()
      .build())
  }

  test("should solve pattern comprehensions for ForeachApply") {
    val q =
      """
        |FOREACH (num IN [reduce(sum=0, x IN [(a)-->(b) | b.age] | sum + x)] |
        |  MERGE ({foo: num}) )
      """.stripMargin

    val plan = planner.plan(q).stripProduceResults

    plan should equal(planner.subPlanBuilder()
      .emptyResult()
      .foreachApply("num", "[reduce(sum = 0, x IN anon_3 | sum + x)]")
      .|.merge(Seq(createNodeWithProperties("anon_1", Seq(), "{foo: num}")))
      .|.filter("anon_1.foo = num")
      .|.allNodeScan("anon_1", "num")
      .rollUpApply("anon_3", "anon_2")
      .|.projection("b.age AS anon_2")
      .|.allRelationshipsScan("(a)-[anon_0]->(b)")
      .argument()
      .build())
  }

  test("should solve pattern comprehensions for Foreach") {
    val q =
      """
        |FOREACH (num IN [reduce(sum=0, x IN [(a)-->(b) | b.age] | sum + x)] |
        |  CREATE ({foo: num}) )
      """.stripMargin

    val plan = planner.plan(q).stripProduceResults

    plan should equal(planner.subPlanBuilder()
      .emptyResult()
      .foreach(
        "num",
        "[reduce(sum = 0, x IN anon_3 | sum + x)]",
        Seq(createPattern(Seq(createNodeWithProperties("anon_1", Seq(), "{foo: num}")), Seq()))
      )
      .rollUpApply("anon_3", "anon_2")
      .|.projection("b.age AS anon_2")
      .|.allRelationshipsScan("(a)-[anon_0]->(b)")
      .argument()
      .build())
  }

  test("should not use RollupApply for PatternComprehensions in head") {
    val q =
      """
        |MATCH (a)
        |WHERE head( [(a)<--(b) | b.prop4 = true] ) = true
        |RETURN a
      """.stripMargin

    val plan = planner.plan(q)
    plan.folder.treeExists({
      case _: RollUpApply => true
    }) should be(false)
  }

  test("should not use RollupApply for PatternComprehensions in container index") {
    val q =
      """
        |MATCH (a)
        |WHERE [(a)<--(b) | b.prop4 = true][2]
        |RETURN a
      """.stripMargin

    val plan = planner.plan(q)
    plan.folder.treeExists({
      case _: RollUpApply => true
    }) should be(false)
  }

  test("should solve and name pattern comprehensions with NestedPlanExpression for Projection") {
    val q =
      """
        |MATCH (n)
        |RETURN [(n)-->(b) | b.age][1] AS age
        """.stripMargin

    val plan = planner.plan(q).stripProduceResults

    val expectedNestedPlan = planner.subPlanBuilder()
      .limit(add(literalInt(1), literalInt(1)))
      .projection("b.age AS anon_1")
      .expand("(n)-[anon_0]->(b)")
      .argument("n")
      .build()

    plan should equal(
      planner.subPlanBuilder()
        .projection(
          project = Map("age" -> containerIndex(
            NestedPlanCollectExpression(expectedNestedPlan, varFor("anon_1"), "[(n)-[`anon_0`]->(b) | b.age]")(pos),
            literalInt(1)
          )),
          discard = Set("n")
        )
        .allNodeScan("n")
        .build()
    )
  }

  test("should solve and name pattern expressions with NestedPlanExpression for Projection") {
    val q =
      """
        |MATCH (n)
        |RETURN head([exists( (n)-->()-->() )]) AS foo
        """.stripMargin

    val plan = planner.plan(q).stripProduceResults

    val expectedNestedPlan = planner.subPlanBuilder()
      .filter("not anon_2 = anon_0")
      .expand("(anon_1)-[anon_2]->(anon_3)")
      .expand("(n)-[anon_0]->(anon_1)")
      .argument("n")
      .build()

    plan should equal(
      planner.subPlanBuilder()
        .projection(
          project = Map("foo" ->
            Head(listOf(
              NestedPlanExistsExpression(
                expectedNestedPlan,
                s"EXISTS { MATCH (n)-[`anon_0`]->(`anon_1`)-[`anon_2`]->(`anon_3`)$NL  WHERE not `anon_2` = `anon_0` }"
              )(pos)
            ))(pos)),
          discard = Set("n")
        )
        .allNodeScan("n")
        .build()
    )
  }

  test("should solve and name pattern expressions with inlined Label filter with NestedPlanExpression for Projection") {
    val q =
      """
        |MATCH (n)
        |RETURN head([exists( (n)-->(:B) )]) AS foo
        """.stripMargin

    val plan = planner.plan(q).stripProduceResults

    val expectedNestedPlan = planner.subPlanBuilder()
      .filter("anon_1:B")
      .expand("(n)-[anon_0]->(anon_1)")
      .argument("n")
      .build()

    plan should equal(
      planner.subPlanBuilder()
        .projection(
          project = Map("foo" ->
            Head(listOf(
              NestedPlanExistsExpression(
                expectedNestedPlan,
                s"EXISTS { MATCH (n)-[`anon_0`]->(`anon_1`)$NL  WHERE `anon_1`:B }"
              )(pos)
            ))(pos)),
          discard = Set("n")
        )
        .allNodeScan("n")
        .build()
    )
  }

  test(
    "should solve and name pattern expressions with two disjoint inlined Label filters with NestedPlanExpression for Projection"
  ) {
    val q =
      """
        |MATCH (n)
        |RETURN head([exists( (n)-->(:B|C) )]) AS foo
        """.stripMargin

    val plan = planner.plan(q).stripProduceResults

    val expectedNestedPlan = planner.subPlanBuilder()
      .filterExpression(hasAnyLabel("anon_1", "B", "C"))
      .expand("(n)-[anon_0]->(anon_1)")
      .argument("n")
      .build()

    plan should equal(
      planner.subPlanBuilder()
        .projection(
          project = Map("foo" ->
            Head(listOf(
              NestedPlanExistsExpression(
                expectedNestedPlan,
                s"EXISTS { MATCH (n)-[`anon_0`]->(`anon_1`)$NL  WHERE `anon_1`:B OR `anon_1`:C }"
              )(pos)
            ))(pos)),
          discard = Set("n")
        )
        .allNodeScan("n")
        .build()
    )
  }

  test("should use index seeks for subqueries if suitable") {
    val q =
      """
        |MATCH (n)
        |RETURN head([exists( (n)-->(:D {prop: 5}) )]) AS foo
        """.stripMargin

    val plan = planner.plan(q).stripProduceResults

    val expectedNestedPlan = planner.subPlanBuilder()
      .expandInto("(n)-[anon_0]->(anon_1)")
      .nodeIndexOperator("anon_1:D(prop=5)", argumentIds = Set("n"))
      .build()

    plan should equal(
      planner.subPlanBuilder()
        .projection(
          project = Map("foo" ->
            Head(listOf(
              NestedPlanExistsExpression(
                expectedNestedPlan,
                s"EXISTS { MATCH (n)-[`anon_0`]->(`anon_1`)$NL  WHERE `anon_1`.prop IN [5] AND `anon_1`:D }"
              )(
                pos
              )
            ))(pos)),
          discard = Set("n")
        )
        .allNodeScan("n")
        .build()
    )
  }

  test("should not use RollupApply for PatternComprehensions in list slice to") {
    val q =
      """
        |MATCH (a)
        |WHERE NOT isEmpty([(a)<--(b) | b.prop4 = true][..5])
        |RETURN a
      """.stripMargin

    val plan = planner.plan(q)
    plan.folder.treeExists({
      case _: RollUpApply => true
    }) should be(false)
  }

  test("should not use RollupApply for PatternComprehensions in list slice from/to") {
    val q =
      """
        |MATCH (a)
        |WHERE NOT isEmpty([(a)<--(b) | b.prop4 = true ][2..5])
        |RETURN a
      """.stripMargin

    val plan = planner.plan(q)
    plan.folder.treeExists({
      case _: RollUpApply => true
    }) should be(false)
  }

  test("should solve pattern comprehension in size function with GetDegree") {
    val q =
      """
        |MATCH (a)
        |RETURN size([p=(a)-[:FOO]->() | p]) AS size
      """.stripMargin

    val plan = planner.plan(q).stripProduceResults

    plan should equal(planner.subPlanBuilder()
      .projection(
        project = Map(
          "size" -> GetDegree(varFor("a"), Some(RelTypeName("FOO")(pos)), SemanticDirection.OUTGOING)(pos)
        ),
        discard = Set("a")
      )
      .allNodeScan("a")
      .build())
  }

  test("should solve pattern expression in exists with GetDegree") {
    val q =
      """
        |MATCH (a)
        |RETURN exists((a)-[:FOO]->()) AS exists
      """.stripMargin

    val plan = planner.plan(q).stripProduceResults

    plan should equal(planner.subPlanBuilder()
      .projection(
        project = Map("exists" -> HasDegreeGreaterThan(
          varFor("a"),
          Some(RelTypeName("FOO")(pos)),
          SemanticDirection.OUTGOING,
          literalInt(0)
        )(pos)),
        discard = Set("a")
      )
      .allNodeScan("a")
      .build())
  }

  test("should solve COUNT expression with GetDegree") {
    val q =
      """
        |MATCH (a)
        |RETURN COUNT { (a)-[:FOO]->() } AS foos
      """.stripMargin

    val plan = planner.plan(q).stripProduceResults

    plan should equal(planner.subPlanBuilder()
      .projection(
        project = Map("foos" -> GetDegree(
          varFor("a"),
          Some(RelTypeName("FOO")(pos)),
          SemanticDirection.OUTGOING
        )(pos)),
        discard = Set("a")
      )
      .allNodeScan("a")
      .build())
  }

  test("should solve COUNT inequality expression with GetDegree") {
    val q =
      """
        |MATCH (a)
        |RETURN COUNT { (a)-[:FOO]->() } > 10 AS moreThan10Foos
      """.stripMargin

    val plan = planner.plan(q).stripProduceResults

    plan should equal(planner.subPlanBuilder()
      .projection(
        project = Map("moreThan10Foos" -> HasDegreeGreaterThan(
          varFor("a"),
          Some(RelTypeName("FOO")(pos)),
          SemanticDirection.OUTGOING,
          literalInt(10)
        )(pos)),
        discard = Set("a")
      )
      .allNodeScan("a")
      .build())
  }

  test("should honor relationship uniqueness for pattern expression") {
    val logicalPlan = planner.plan("MATCH (a) WHERE (a)-->()-->() RETURN a")
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("a")
        .semiApply()
        .|.filter("not anon_2 = anon_0") // This filter upholds rel uniqueness
        .|.expandAll("(anon_1)-[anon_2]->(anon_3)")
        .|.expandAll("(a)-[anon_0]->(anon_1)")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test("should honor relationship uniqueness for pattern comprehension") {
    val logicalPlan = planner.plan("MATCH (a) RETURN [(a)-->(b)-->(c) | c.prop] AS props")
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("props")
        .rollUpApply("props", "anon_2")
        .|.projection("c.prop AS anon_2")
        .|.filter("not anon_1 = anon_0") // This filter upholds rel uniqueness
        .|.expandAll("(b)-[anon_1]->(c)")
        .|.expandAll("(a)-[anon_0]->(b)")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test("Subquery plan with SemiApply should plan predicates in the right order according to LabelInfo") {
    val logicalPlan = planner.plan(
      """MATCH (a:ManyProps), (b:SomeProps), (c:FewProps)
        |WHERE (a {prop: 0})--(b {prop: 0})--(c {prop: 0})
        |RETURN *
        |""".stripMargin
    )

    // There are 2 filters in the plan, we are interested in the 2nd
    val filter = logicalPlan.folder.findAllByClass[Selection].apply(1)

    val aProp = equals(cachedNodeProp("a", "prop"), literalInt(0))
    val bProp = equals(cachedNodeProp("b", "prop"), literalInt(0))
    val cProp = equals(cachedNodeProp("c", "prop"), literalInt(0))

    // Should filter in best order according to selectivity: c.prop, b.prop, a.prop
    filter should beLike {
      // We cannot use "plan should equal ..." because equality for [[Ands]] is overridden to not care about the order.
      // But unapply takes the order into account for [[Ands]].
      case Selection(
          Ands(SetExtractor(
            `cProp`,
            `bProp`,
            `aProp`
          )),
          Argument(VariableSet("a", "b", "c"))
        ) => ()
    }
  }

  test("Subquery plan with RollUpApply should plan predicates in the right order according to LabelInfo") {
    val logicalPlan = planner.plan(
      """MATCH (a:ManyProps), (b:SomeProps), (c:FewProps)
        |RETURN [(a {prop: 0})--(b {prop: 0})--(c {prop: 0}) | 1] AS foo
        |""".stripMargin
    )

    // There are 2 filters in the plan, we are interested in the 2nd:
    //  .filter("not anon_2 = anon_3")
    //  .expandInto("(a)-[anon_2]-(b)")
    //  .expandInto("(b)-[anon_3]-(c)")
    //  .filter("cacheN[c.prop] = 0", "cacheN[b.prop] = 0", "cacheN[a.prop] = 0") <- this one
    val filter = logicalPlan.folder.findAllByClass[Selection].apply(1)

    val aProp = equals(cachedNodeProp("a", "prop"), literalInt(0))
    val bProp = equals(cachedNodeProp("b", "prop"), literalInt(0))
    val cProp = equals(cachedNodeProp("c", "prop"), literalInt(0))

    // Should filter in best order according to selectivity: c.prop, b.prop, a.prop
    filter should beLike {
      // We cannot use "plan should equal ..." because equality for [[Ands]] is overridden to not care about the order.
      // But unapply takes the order into account for [[Ands]].
      case Selection(
          Ands(SetExtractor(
            `cProp`,
            `bProp`,
            `aProp`
          )),
          Argument(VariableSet("a", "b", "c"))
        ) => ()
    }
  }

  test("COUNT subquery plan should plan predicates in the right order according to LabelInfo") {
    val logicalPlan = planner.plan(
      """MATCH (a:ManyProps), (b:SomeProps), (c:FewProps)
        |RETURN COUNT { (a {prop: 0})--(b {prop: 0})--(c {prop: 0}) } AS foo
        |""".stripMargin
    )

    // There are 2 filters in the plan, we are interested in the 2nd:
    //  .filter("not anon_2 = anon_3")
    //  .expandInto("(a)-[anon_2]-(b)")
    //  .expandInto("(b)-[anon_3]-(c)")
    //  .filter("cacheN[c.prop] = 0", "cacheN[b.prop] = 0", "cacheN[a.prop] = 0") <- this one
    val filter = logicalPlan.folder.findAllByClass[Selection].apply(1)

    val aProp = equals(cachedNodeProp("a", "prop"), literalInt(0))
    val bProp = equals(cachedNodeProp("b", "prop"), literalInt(0))
    val cProp = equals(cachedNodeProp("c", "prop"), literalInt(0))

    // Should filter in best order according to selectivity: c.prop, b.prop, a.prop
    filter should beLike {
      // We cannot use "plan should equal ..." because equality for [[Ands]] is overridden to not care about the order.
      // But unapply takes the order into account for [[Ands]].
      case Selection(
          Ands(SetExtractor(
            `cProp`,
            `bProp`,
            `aProp`
          )),
          Argument(VariableSet("a", "b", "c"))
        ) => ()
    }
  }

  test("Subquery plan with NestedPlanExpression should plan predicates in the right order according to LabelInfo") {
    val logicalPlan = planner.plan(
      """MATCH (a:ManyProps), (b:SomeProps), (c:FewProps)
        |RETURN [(a {prop: 0})--(b {prop: 0})--(c {prop: 0}) | 1][1] AS foo
        |""".stripMargin
    )

    // There are 2 filters in the plan, we are interested in the 2nd:
    // .filter("not anon_2 = anon_3")
    // .expandInto("(a)-[anon_2]-(b)")
    // .expandInto("(b)-[anon_3]-(c)")
    // .filter("c.prop = 0", "b.prop = 0", "a.prop = 0")  <- this one
    val filter = logicalPlan.folder.findAllByClass[Selection].apply(1)

    val aProp = equals(prop("a", "prop"), literalInt(0))
    val bProp = equals(prop("b", "prop"), literalInt(0))
    val cProp = equals(prop("c", "prop"), literalInt(0))

    // Should filter in best order according to selectivity: c.prop, b.prop, a.prop
    filter should beLike {
      // We cannot use "plan should equal ..." because equality for [[Ands]] is overridden to not care about the order.
      // But unapply takes the order into account for [[Ands]].
      case Selection(
          Ands(SetExtractor(
            `cProp`,
            `bProp`,
            `aProp`
          )),
          Argument(VariableSet("a", "b", "c"))
        ) => ()
    }
  }

  test("Extra horizon: Subquery plan with SemiApply should plan predicates in the right order according to LabelInfo") {
    val logicalPlan = planner.plan(
      """MATCH (a:ManyProps), (b:SomeProps), (c:FewProps)
        |WITH * SKIP 0
        |MATCH (n)
        |WHERE (a {prop: 0})--(b {prop: 0})--(c {prop: 0})
        |RETURN *
        |""".stripMargin
    )

    // There are 2 filters in the plan, we are interested in the 2nd
    val filter = logicalPlan.folder.findAllByClass[Selection].apply(1)

    val aProp = equals(cachedNodeProp("a", "prop"), literalInt(0))
    val bProp = equals(cachedNodeProp("b", "prop"), literalInt(0))
    val cProp = equals(cachedNodeProp("c", "prop"), literalInt(0))

    // Should filter in best order according to selectivity: c.prop, b.prop, a.prop
    filter should beLike {
      // We cannot use "plan should equal ..." because equality for [[Ands]] is overridden to not care about the order.
      // But unapply takes the order into account for [[Ands]].
      case Selection(
          Ands(SetExtractor(
            `cProp`,
            `bProp`,
            `aProp`
          )),
          Argument(VariableSet("a", "b", "c"))
        ) => ()
    }
  }

  test("Should plan semiApply with projectEndPoints for EXISTS with already bound variables") {
    val logicalPlan = planner.plan(
      "MATCH (n)-[r]->(m), (o)-[r2]->(m)-[r3]->(q) WHERE EXISTS { (n)-[r]->(m), (o)-[r2]->(m)-[r3]->(q) WHERE n.foo > 5} RETURN *"
    )
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("m", "n", "o", "q", "r", "r2", "r3")
        .semiApply()
        .|.projectEndpoints("(n)-[r]->(m)", startInScope = true, endInScope = true)
        .|.projectEndpoints("(o)-[r2]->(m)", startInScope = true, endInScope = true)
        .|.projectEndpoints("(m)-[r3]->(q)", startInScope = true, endInScope = true)
        .|.filter("n.foo > 5", "not r = r3", "not r = r2", "not r3 = r2")
        .|.argument("n", "m", "q", "r2", "r", "r3", "o")
        .filter("not r = r3", "not r = r2")
        .expandAll("(m)<-[r]-(n)")
        .filter("not r3 = r2")
        .expandAll("(m)<-[r2]-(o)")
        .allRelationshipsScan("(m)-[r3]->(q)")
        .build()
    )
  }

  test("Should plan semiApply with expands for EXISTS with new variables") {
    val logicalPlan = planner.plan("MATCH (n)-[r]->(m) WHERE EXISTS { (o)-[r2]->(m)-[r3]->(q) } RETURN *")
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("m", "n", "r")
        .semiApply()
        .|.filter("not r3 = r2")
        .|.expandAll("(m)<-[r2]-(o)")
        .|.expandAll("(m)-[r3]->(q)")
        .|.argument("m")
        .allRelationshipsScan("(n)-[r]->(m)")
        .build()
    )
  }

  test(
    "in an expression with 2 PatternComprehensions, use RollupApply for one and NestedPlanExpression for the other"
  ) {
    // Here we have an expression that contains 2 PatternComprehensions. One of them can be solved with RollUpApply, the other cannot.
    val q =
      """
        |MATCH (a)
        |RETURN [[(a)<--(b) | b.prop4 = true][2], [(a)<--(b) | b.prop4 = true]] AS list
      """.stripMargin

    val plan = planner.plan(q)

    val expectedNestedPlan = planner.subPlanBuilder()
      .limit(add(literalInt(1), literalInt(2)))
      .projection("b.prop4 = true AS anon_4")
      .expandAll("(a)<-[anon_0]-(b)")
      .argument("a")
      .build()

    val expectedExpression = listOf(
      containerIndex(
        NestedPlanCollectExpression(
          expectedNestedPlan,
          varFor("anon_4"),
          "[(a)<-[`anon_0`]-(`b`) | `b`.prop4 IN [true]]"
        )(pos),
        literalInt(2)
      ),
      varFor("anon_7")
    )

    plan.stripProduceResults should equal(
      planner.subPlanBuilder()
        .projection(Map("list" -> expectedExpression), discard = Set("a", "anon_7"))
        .rollUpApply("anon_7", "anon_6")
        .|.projection("b.prop4 = true AS anon_6")
        .|.expandAll("(a)<-[anon_1]-(b)")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test("should get relationship count from store for a simple COUNT expression") {
    val plan = planner.plan("RETURN COUNT { ()-[]->() } AS result").stripProduceResults
    plan shouldBe planner.subPlanBuilder()
      .relationshipCountFromCountStore("result", None, Seq.empty, None)
      .build()
  }

  test("should get relationship count from store for a simple nested COUNT expression") {
    val query =
      """MATCH (n)
        |RETURN CASE n.prop
        |  WHEN true THEN COUNT { (a)-[r:REL]->(b) }
        |  WHEN false THEN COUNT { (c:Person)-[k:KNOWS]->(d) }
        |END AS result""".stripMargin

    val plan = planner.plan(query).stripProduceResults
    plan.folder.findAllByClass[NestedPlanGetByNameExpression].toSet shouldBe Set(
      NestedPlanGetByNameExpression(
        planner.subPlanBuilder()
          .relationshipCountFromCountStore("anon_0", None, Seq("REL"), None)
          .build(),
        varFor("anon_0"),
        "COUNT { MATCH (a)-[r:REL]->(b) }"
      )(pos),
      NestedPlanGetByNameExpression(
        planner.subPlanBuilder()
          .relationshipCountFromCountStore("anon_1", Some("Person"), Seq("KNOWS"), None)
          .build(),
        varFor("anon_1"),
        s"COUNT { MATCH (c)-[k:KNOWS]->(d)$NL  WHERE c:Person }"
      )(pos)
    )
  }

  test("should plan COUNT expression with multiple patterns") {
    val plan = planner.plan(
      "MATCH (a:Foo) RETURN COUNT { MATCH (a)-[r]->(b), (a)<-[r2]-(b) } AS bidirectionalConnections"
    ).stripProduceResults
    plan shouldBe planner.subPlanBuilder()
      .apply()
      .|.aggregation(Seq.empty, Seq("count(*) AS bidirectionalConnections"))
      .|.filter("not r = r2")
      .|.expandInto("(a)-[r]->(b)")
      .|.expandAll("(a)<-[r2]-(b)")
      .|.argument("a")
      .nodeByLabelScan("a", "Foo")
      .build()
  }

  test("should plan COUNT expression as equality check") {
    val plan = planner.plan(
      "MATCH (a:Foo) RETURN COUNT { MATCH (a)-[r]->(b), (a)<-[r2]-(b) } = 2 AS foo"
    ).stripProduceResults
    plan shouldBe planner.subPlanBuilder()
      .projection(project = Seq("anon_0 = 2 AS foo"), discard = Set("a", "anon_0"))
      .apply()
      .|.aggregation(Seq.empty, Seq("count(*) AS anon_0"))
      .|.filter("not r = r2")
      .|.expandInto("(a)-[r]->(b)")
      .|.expandAll("(a)<-[r2]-(b)")
      .|.argument("a")
      .nodeByLabelScan("a", "Foo")
      .build()
  }

  test("should plan COUNT expression with multiple disconnected patterns") {
    val plan = planner.plan(
      "MATCH (a:Person), (b:Person) RETURN COUNT { MATCH (a)-[r:KNOWS]->(c), (b)-[r2:KNOWS]->(d) WHERE c.name = d.name } AS sameNameFriends"
    ).stripProduceResults
    plan shouldBe planner.subPlanBuilder()
      .apply()
      .|.aggregation(Seq.empty, Seq("count(*) AS sameNameFriends"))
      .|.filter("not r = r2", "c.name = d.name")
      .|.expandAll("(a)-[r:KNOWS]->(c)")
      .|.expandAll("(b)-[r2:KNOWS]->(d)")
      .|.argument("a", "b")
      .cartesianProduct()
      .|.nodeByLabelScan("b", "Person")
      .nodeByLabelScan("a", "Person")
      .build()
  }

  test("should plan COUNT expression with UNION") {
    val plan = planner.plan(
      "MATCH (a:Person), (b:Person) RETURN COUNT { MATCH (a)-[r:KNOWS]->(c) RETURN a AS foo UNION MATCH (b)-[r2:KNOWS]->(d) RETURN b AS foo } AS sameNameFriends"
    ).stripProduceResults
    plan shouldBe planner.subPlanBuilder()
      .apply()
      .|.aggregation(Seq.empty, Seq("count(*) AS sameNameFriends"))
      .|.distinct("b AS b", "a AS a", "foo AS foo")
      .|.union()
      .|.|.projection("foo AS foo")
      .|.|.projection("b AS foo")
      .|.|.expandAll("(b)-[r2:KNOWS]->(d)")
      .|.|.argument("a", "b")
      .|.projection("foo AS foo")
      .|.projection("a AS foo")
      .|.expandAll("(a)-[r:KNOWS]->(c)")
      .|.argument("a", "b")
      .cartesianProduct()
      .|.nodeByLabelScan("b", "Person", IndexOrderNone)
      .nodeByLabelScan("a", "Person", IndexOrderNone)
      .build()
  }

  test("should plan FULL COUNT expression") {
    val plan = planner.plan(
      "MATCH (a:Person) RETURN COUNT { MATCH (a)-[r:KNOWS]->(c) RETURN a } AS foo"
    ).stripProduceResults
    plan should equal(planner.subPlanBuilder()
      .projection(
        project =
          Map("foo" -> GetDegree(varFor("a"), Some(RelTypeName("KNOWS")(pos)), SemanticDirection.OUTGOING)(pos)),
        discard = Set("a")
      )
      .nodeByLabelScan("a", "Person", IndexOrderNone)
      .build())
  }

  test("should plan FULL COUNT expression with ignored ORDER BY") {
    val plan = planner.plan(
      "MATCH (a:Person) WHERE COUNT { MATCH (a)-[r:KNOWS]->(c) RETURN a ORDER BY a } > 1 RETURN a"
    ).stripProduceResults
    plan should equal(planner.subPlanBuilder()
      .filterExpression(HasDegreeGreaterThan(
        varFor("a"),
        Some(RelTypeName("KNOWS")(pos)),
        SemanticDirection.OUTGOING,
        literalInt(1)
      )(pos))
      .nodeByLabelScan("a", "Person", IndexOrderNone)
      .build())
  }

  test("should plan FULL COUNT expression with SKIP") {
    val plan = planner.plan(
      "MATCH (a:Person) WHERE COUNT { MATCH (a)-[r:KNOWS]->(c) RETURN a SKIP 2 } > 1 RETURN a"
    ).stripProduceResults
    plan should equal(planner.subPlanBuilder()
      .filter("anon_0 > 1")
      .apply()
      .|.aggregation(Seq(), Seq("count(*) AS anon_0"))
      .|.skip(2L)
      .|.expandAll("(a)-[r:KNOWS]->(c)")
      .|.argument("a")
      .nodeByLabelScan("a", "Person", IndexOrderNone)
      .build())
  }

  test("should plan FULL COUNT expression with LIMIT") {
    val plan = planner.plan(
      "MATCH (a:Person) WHERE COUNT { MATCH (a)-[r:KNOWS]->(c) RETURN a LIMIT 42 } > 1 RETURN a"
    ).stripProduceResults
    plan should equal(planner.subPlanBuilder()
      .filter("anon_0 > 1")
      .apply()
      .|.aggregation(Seq(), Seq("count(*) AS anon_0"))
      .|.limit(42L)
      .|.expandAll("(a)-[r:KNOWS]->(c)")
      .|.argument("a")
      .nodeByLabelScan("a", "Person", IndexOrderNone)
      .build())
  }

  test("should plan FULL COUNT expression with ORDER BY, SKIP and LIMIT") {
    val plan = planner.plan(
      "MATCH (a:Person) WHERE COUNT { MATCH (a)-[r:KNOWS]->(c) RETURN a ORDER BY a SKIP 7 LIMIT 42 } > 1 RETURN a"
    ).stripProduceResults
    plan should equal(planner.subPlanBuilder()
      .filter("anon_0 > 1")
      .apply()
      .|.aggregation(Seq(), Seq("count(*) AS anon_0"))
      .|.skip(7L)
      .|.top(
        Seq(Ascending(varFor("a"))),
        add(SignedDecimalIntegerLiteral("42")(pos), SignedDecimalIntegerLiteral("7")(pos))
      )
      .|.expandAll("(a)-[r:KNOWS]->(c)")
      .|.argument("a")
      .nodeByLabelScan("a", "Person", IndexOrderNone)
      .build())
  }

  test("should plan FULL COUNT expression with DISTINCT") {
    val plan = planner.plan(
      "MATCH (a:Person) WHERE COUNT { MATCH (a)-[r:KNOWS]->(c) RETURN DISTINCT a } > 1 RETURN a"
    ).stripProduceResults
    plan should equal(planner.subPlanBuilder()
      .filter("anon_0 > 1")
      .apply()
      .|.aggregation(Seq(), Seq("count(*) AS anon_0"))
      .|.distinct("a AS a")
      .|.expandAll("(a)-[r:KNOWS]->(c)")
      .|.argument("a")
      .nodeByLabelScan("a", "Person", IndexOrderNone)
      .build())
  }

  test("should plan FULL COUNT expression with a CALL Subquery") {
    val plan = planner.plan(
      "MATCH (a:Person) RETURN COUNT { MATCH (a)-[r:KNOWS]->(c) CALL { WITH a MATCH (a)-[:HAS_DOG]-(b) RETURN b} RETURN a } AS foo"
    ).stripProduceResults
    plan shouldBe planner.subPlanBuilder()
      .apply()
      .|.aggregation(Seq(), Seq("count(*) AS foo"))
      .|.expandAll("(a)-[anon_0:HAS_DOG]-(b)")
      .|.expandAll("(a)-[r:KNOWS]->(c)")
      .|.argument("a")
      .nodeByLabelScan("a", "Person", IndexOrderNone)
      .build()
  }

  test("should plan FULL COUNT expression with a CALL Subquery and ignored ORDER BY") {
    val plan = planner.plan(
      "MATCH (a:Person) RETURN COUNT { MATCH (a)-[r:KNOWS]->(c) CALL { WITH a MATCH (a)-[:HAS_DOG]-(b) RETURN b} RETURN a ORDER BY a} AS foo"
    ).stripProduceResults
    plan shouldBe planner.subPlanBuilder()
      .apply()
      .|.aggregation(Seq(), Seq("count(*) AS foo"))
      .|.expandAll("(a)-[anon_0:HAS_DOG]-(b)")
      .|.expandAll("(a)-[r:KNOWS]->(c)")
      .|.argument("a")
      .nodeByLabelScan("a", "Person", IndexOrderNone)
      .build()
  }

  test("should plan nested COUNT in RETURN") {
    val logicalPlan = planner.plan("MATCH (a) RETURN [COUNT { (a)-[:X]->(:Foo) }] AS counts")
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("counts")
        .projection(project = Seq("[anon_2] AS counts"), discard = Set("a", "anon_2"))
        .apply()
        .|.aggregation(Seq(), Seq("count(*) AS anon_2"))
        .|.filter("anon_1:Foo")
        .|.expandAll("(a)-[anon_0:X]->(anon_1)")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test("should use GetDegree to plan EXISTS in RETURN") {
    val logicalPlan = planner.plan("MATCH (a) RETURN EXISTS { (a)-[:X]->() } AS exists")
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("exists")
        .projection(
          project = Map("exists" -> HasDegreeGreaterThan(
            varFor("a"),
            Some(RelTypeName("X")(pos)),
            SemanticDirection.OUTGOING,
            literalInt(0)
          )(pos)),
          discard = Set("a")
        )
        .allNodeScan("a")
        .build()
    )
  }

  test("should plan EXISTS expression with UNION") {
    val plan = planner.plan(
      "MATCH (a:Person), (b:Person) RETURN EXISTS { MATCH (a)-[r:KNOWS]->(c) RETURN a AS foo UNION MATCH (b)-[r2:KNOWS]->(d) RETURN b AS foo } AS sameNameFriends"
    ).stripProduceResults
    plan shouldBe planner.subPlanBuilder()
      .letSemiApply("sameNameFriends")
      .|.distinct("b AS b", "a AS a", "foo AS foo")
      .|.union()
      .|.|.projection("foo AS foo")
      .|.|.projection("b AS foo")
      .|.|.expandAll("(b)-[r2:KNOWS]->(d)")
      .|.|.argument("a", "b")
      .|.projection("foo AS foo")
      .|.projection("a AS foo")
      .|.expandAll("(a)-[r:KNOWS]->(c)")
      .|.argument("a", "b")
      .cartesianProduct()
      .|.nodeByLabelScan("b", "Person", IndexOrderNone)
      .nodeByLabelScan("a", "Person", IndexOrderNone)
      .build()
  }

  test("should plan FULL EXISTS expression with ORDER BY") {
    val plan = planner.plan(
      "MATCH (a:Person) WHERE EXISTS { MATCH (a)-[r:KNOWS]->(c) RETURN a ORDER BY a} RETURN a"
    ).stripProduceResults
    plan should equal(planner.subPlanBuilder()
      .semiApply()
      .|.sort("a ASC")
      .|.expandAll("(a)-[r:KNOWS]->(c)")
      .|.argument("a")
      .nodeByLabelScan("a", "Person", IndexOrderNone)
      .build())
  }

  test("should plan FULL EXISTS expression with SKIP") {
    val plan = planner.plan(
      "MATCH (a:Person) WHERE EXISTS { MATCH (a)-[r:KNOWS]->(c) RETURN a SKIP 7} RETURN a"
    ).stripProduceResults
    plan should equal(planner.subPlanBuilder()
      .semiApply()
      .|.skip(7L)
      .|.expandAll("(a)-[r:KNOWS]->(c)")
      .|.argument("a")
      .nodeByLabelScan("a", "Person", IndexOrderNone)
      .build())
  }

  test("should plan FULL EXISTS expression with LIMIT") {
    val plan = planner.plan(
      "MATCH (a:Person) WHERE EXISTS { MATCH (a)-[r:KNOWS]->(c) RETURN a LIMIT 42} RETURN a"
    ).stripProduceResults
    plan should equal(planner.subPlanBuilder()
      .semiApply()
      .|.limit(42L)
      .|.expandAll("(a)-[r:KNOWS]->(c)")
      .|.argument("a")
      .nodeByLabelScan("a", "Person", IndexOrderNone)
      .build())
  }

  test("should plan FULL EXISTS expression with ORDER BY, SKIP and LIMIT") {
    val plan = planner.plan(
      "MATCH (a:Person) WHERE EXISTS { MATCH (a)-[r:KNOWS]->(c) RETURN a ORDER BY a SKIP 7 LIMIT 42 } RETURN a"
    ).stripProduceResults
    plan should equal(planner.subPlanBuilder()
      .semiApply()
      .|.skip(7L)
      .|.top(
        Seq(Ascending(varFor("a"))),
        add(SignedDecimalIntegerLiteral("42")(pos), SignedDecimalIntegerLiteral("7")(pos))
      )
      .|.expandAll("(a)-[r:KNOWS]->(c)")
      .|.argument("a")
      .nodeByLabelScan("a", "Person", IndexOrderNone)
      .build())
  }

  test("should plan FULL EXISTS expression with DISTINCT") {
    val plan = planner.plan(
      "MATCH (a:Person) WHERE EXISTS { MATCH (a)-[r:KNOWS]->(c) RETURN DISTINCT a} RETURN a"
    ).stripProduceResults
    plan should equal(planner.subPlanBuilder()
      .semiApply()
      .|.distinct("a AS a")
      .|.expandAll("(a)-[r:KNOWS]->(c)")
      .|.argument("a")
      .nodeByLabelScan("a", "Person", IndexOrderNone)
      .build())
  }

  test("should plan FULL EXISTS expression with a CALL Subquery") {
    val plan = planner.plan(
      "MATCH (a:Person) RETURN EXISTS { MATCH (a)-[r:KNOWS]->(c) CALL { WITH a MATCH (a)-[:HAS_DOG]-(b) RETURN b} RETURN a } AS foo"
    ).stripProduceResults
    plan shouldBe planner.subPlanBuilder()
      .letSemiApply("foo")
      .|.expandAll("(a)-[anon_0:HAS_DOG]-(b)")
      .|.expandAll("(a)-[r:KNOWS]->(c)")
      .|.argument("a")
      .nodeByLabelScan("a", "Person", IndexOrderNone)
      .build()
  }

  test("should use LetSemiApply to plan EXISTS in RETURN") {
    val logicalPlan = planner.plan("MATCH (a) RETURN EXISTS { (a)-[:X]->(:Foo) } AS exists")
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("exists")
        .letSemiApply("exists")
        .|.filter("anon_1:Foo")
        .|.expandAll("(a)-[anon_0:X]->(anon_1)")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test("should use LetSemiApply to plan EXISTS in AND in RETURN") {
    val logicalPlan = planner.plan("MATCH (a) RETURN EXISTS { (a)-[:X]->(:Foo) } AND a.prop = 10 AS exists")
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("exists")
        .projection(project = Seq("anon_2 AND a.prop = 10 AS exists"), discard = Set("a", "anon_2"))
        .letSemiApply("anon_2")
        .|.filter("anon_1:Foo")
        .|.expandAll("(a)-[anon_0:X]->(anon_1)")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test("should use LetAntiSemiApply to plan NOT EXISTS in RETURN") {
    val logicalPlan = planner.plan("MATCH (a) RETURN NOT EXISTS { (a)-[:X]->(:Foo) } AS notExists")
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("notExists")
        .letAntiSemiApply("notExists")
        .|.filter("anon_1:Foo")
        .|.expandAll("(a)-[anon_0:X]->(anon_1)")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test("should use LetAntiSemiApply to plan nested NOT EXISTS in RETURN") {
    val logicalPlan = planner.plan("MATCH (a) RETURN [NOT EXISTS { (a)-[:X]->(:Foo) }] AS notExists")
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("notExists")
        .projection(project = Seq("[anon_2] AS notExists"), discard = Set("a", "anon_2"))
        .letAntiSemiApply("anon_2")
        .|.filter("anon_1:Foo")
        .|.expandAll("(a)-[anon_0:X]->(anon_1)")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test("should use LetAntiSemiApply to plan NOT EXISTS in AND in RETURN") {
    val logicalPlan = planner.plan("MATCH (a) RETURN NOT EXISTS { (a)-[:X]->(:Foo) } AND a.prop = 10 AS notExists")
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("notExists")
        .projection(project = Seq("anon_2 AND a.prop = 10 AS notExists"), discard = Set("a", "anon_2"))
        .letAntiSemiApply("anon_2")
        .|.filter("anon_1:Foo")
        .|.expandAll("(a)-[anon_0:X]->(anon_1)")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test("should use LetSelectOrSemiApply to plan EXISTS ORed with another predicate in RETURN") {
    val logicalPlan = planner.plan("MATCH (a) RETURN EXISTS { (a)-[:X]->(:Foo) } OR a.prop = 10 AS exists")
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("exists")
        .projection(
          project = Seq("anon_2 AS exists"),
          discard = Set("a", "anon_2")
        ) // We could remove this unneeded projection, but it doesn't do much harm
        .letSelectOrSemiApply("anon_2", "a.prop = 10")
        .|.filter("anon_1:Foo")
        .|.expandAll("(a)-[anon_0:X]->(anon_1)")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test("should use LetSelectOrSemiApply to plan nested EXISTS ORed with another predicate in RETURN") {
    val logicalPlan = planner.plan("MATCH (a) RETURN [EXISTS { (a)-[:X]->(:Foo) } OR a.prop = 10] AS exists")
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("exists")
        .projection(project = Seq("[anon_2] AS exists"), discard = Set("a", "anon_2"))
        .letSelectOrSemiApply("anon_2", "a.prop = 10")
        .|.filter("anon_1:Foo")
        .|.expandAll("(a)-[anon_0:X]->(anon_1)")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test("should use LetSelectOrSemiApply to plan EXISTS ORed with two other predicate in RETURN") {
    val logicalPlan = planner.plan("MATCH (a) RETURN EXISTS { (a)-[:X]->(:Foo) } OR a.prop = 10 OR a.foo = 5 AS exists")
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("exists")
        .projection(
          project = Seq("anon_2 AS exists"),
          discard = Set("a", "anon_2")
        ) // We could remove this unneeded projection, but it doesn't do much harm
        .letSelectOrSemiApply("anon_2", "a.prop = 10 OR a.foo = 5")
        .|.filter("anon_1:Foo")
        .|.expandAll("(a)-[anon_0:X]->(anon_1)")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test("should use LetSelectOrSemiApply to plan nested EXISTS ORed with two other predicate in RETURN") {
    val logicalPlan =
      planner.plan("MATCH (a) RETURN [EXISTS { (a)-[:X]->(:Foo) } OR a.prop = 10 OR a.foo = 5] AS exists")
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("exists")
        .projection(project = Seq("[anon_2] AS exists"), discard = Set("a", "anon_2"))
        .letSelectOrSemiApply("anon_2", "a.prop = 10 OR a.foo = 5")
        .|.filter("anon_1:Foo")
        .|.expandAll("(a)-[anon_0:X]->(anon_1)")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test("should use LetSelectOrAntiSemiApply to plan NOT EXISTS ORed with another predicate in RETURN") {
    val logicalPlan = planner.plan("MATCH (a) RETURN NOT EXISTS { (a)-[:X]->(:Foo) } OR a.prop = 10 AS notExists")
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("notExists")
        .projection(
          project = Seq("anon_2 AS notExists"),
          discard = Set("a", "anon_2")
        ) // We could remove this unneeded projection, but it doesn't do much harm
        .letSelectOrAntiSemiApply("anon_2", "a.prop = 10")
        .|.filter("anon_1:Foo")
        .|.expandAll("(a)-[anon_0:X]->(anon_1)")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test("should use LetSelectOrAntiSemiApply to plan nested NOT EXISTS ORed with another predicate in RETURN") {
    val logicalPlan = planner.plan("MATCH (a) RETURN [NOT EXISTS { (a)-[:X]->(:Foo) } OR a.prop = 10] AS notExists")
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("notExists")
        .projection(project = Seq("[anon_2] AS notExists"), discard = Set("a", "anon_2"))
        .letSelectOrAntiSemiApply("anon_2", "a.prop = 10")
        .|.filter("anon_1:Foo")
        .|.expandAll("(a)-[anon_0:X]->(anon_1)")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test("should use LetSemiApply and LetSelectOrSemiApply to plan two ORed EXISTS in RETURN") {
    val logicalPlan =
      planner.plan("MATCH (a) RETURN EXISTS { (a)-[:X]->(:Foo) } OR EXISTS { (a)-[:X]->(:Bar) } AS exists")
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("exists")
        .projection(
          project = Seq("anon_5 AS exists"),
          discard = Set("a", "anon_4", "anon_5")
        ) // We could remove this unneeded projection, but it doesn't do much harm
        .letSelectOrSemiApply("anon_5", "anon_4")
        .|.filter("anon_3:Bar")
        .|.expandAll("(a)-[anon_2:X]->(anon_3)")
        .|.argument("a")
        .letSemiApply("anon_4")
        .|.filter("anon_1:Foo")
        .|.expandAll("(a)-[anon_0:X]->(anon_1)")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test("should use LetSemiApply and LetSelectOrSemiApply to plan two nested ORed EXISTS in RETURN") {
    val logicalPlan =
      planner.plan("MATCH (a) RETURN [EXISTS { (a)-[:X]->(:Foo) } OR EXISTS { (a)-[:X]->(:Bar) }] AS exists")
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("exists")
        .projection(project = Seq("[anon_5] AS exists"), discard = Set("a", "anon_4", "anon_5"))
        .letSelectOrSemiApply("anon_5", "anon_4")
        .|.filter("anon_3:Bar")
        .|.expandAll("(a)-[anon_2:X]->(anon_3)")
        .|.argument("a")
        .letSemiApply("anon_4")
        .|.filter("anon_1:Foo")
        .|.expandAll("(a)-[anon_0:X]->(anon_1)")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test("should use LetAntiSemiApply and LetSelectOrAntiSemiApply to plan two ORed NOT EXISTS in RETURN") {
    val logicalPlan =
      planner.plan("MATCH (a) RETURN NOT EXISTS { (a)-[:X]->(:Foo) } OR NOT EXISTS { (a)-[:X]->(:Bar) } AS notExists")
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("notExists")
        .projection(
          project = Seq("anon_5 AS notExists"),
          discard = Set("a", "anon_4", "anon_5")
        ) // We could remove this unneeded projection, but it doesn't do much harm
        .letSelectOrAntiSemiApply("anon_5", "anon_4")
        .|.filter("anon_3:Bar")
        .|.expandAll("(a)-[anon_2:X]->(anon_3)")
        .|.argument("a")
        .letAntiSemiApply("anon_4")
        .|.filter("anon_1:Foo")
        .|.expandAll("(a)-[anon_0:X]->(anon_1)")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test("should use LetAntiSemiApply and LetSelectOrAntiSemiApply to plan two nested ORed NOT EXISTS in RETURN") {
    val logicalPlan =
      planner.plan("MATCH (a) RETURN [NOT EXISTS { (a)-[:X]->(:Foo) } OR NOT EXISTS { (a)-[:X]->(:Bar) }] AS notExists")
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("notExists")
        .projection(project = Seq("[anon_5] AS notExists"), discard = Set("a", "anon_4", "anon_5"))
        .letSelectOrAntiSemiApply("anon_5", "anon_4")
        .|.filter("anon_3:Bar")
        .|.expandAll("(a)-[anon_2:X]->(anon_3)")
        .|.argument("a")
        .letAntiSemiApply("anon_4")
        .|.filter("anon_1:Foo")
        .|.expandAll("(a)-[anon_0:X]->(anon_1)")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test(
    "should use LetSelectOrSemiApply and LetSelectOrSemiApply to plan two ORed EXISTS ORed with another predicate in RETURN"
  ) {
    val logicalPlan =
      planner.plan(
        "MATCH (a) RETURN EXISTS { (a)-[:X]->(:Foo) } OR EXISTS { (a)-[:X]->(:Bar) } OR a.prop = 10 AS exists"
      )
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("exists")
        .projection(
          project = Seq("anon_5 AS exists"),
          discard = Set("a", "anon_4", "anon_5")
        ) // We could remove this unneeded projection, but it doesn't do much harm
        .letSelectOrSemiApply("anon_5", "anon_4")
        .|.filter("anon_3:Bar")
        .|.expandAll("(a)-[anon_2:X]->(anon_3)")
        .|.argument("a")
        .letSelectOrSemiApply("anon_4", "a.prop = 10")
        .|.filter("anon_1:Foo")
        .|.expandAll("(a)-[anon_0:X]->(anon_1)")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test(
    "should use LetSelectOrSemiApply and LetSelectOrSemiApply to plan two nested ORed EXISTS ORed with another predicate in RETURN"
  ) {
    val logicalPlan =
      planner.plan(
        "MATCH (a) RETURN [EXISTS { (a)-[:X]->(:Foo) } OR EXISTS { (a)-[:X]->(:Bar) } OR a.prop = 10] AS exists"
      )
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("exists")
        .projection(project = Seq("[anon_5] AS exists"), discard = Set("a", "anon_4", "anon_5"))
        .letSelectOrSemiApply("anon_5", "anon_4")
        .|.filter("anon_3:Bar")
        .|.expandAll("(a)-[anon_2:X]->(anon_3)")
        .|.argument("a")
        .letSelectOrSemiApply("anon_4", "a.prop = 10")
        .|.filter("anon_1:Foo")
        .|.expandAll("(a)-[anon_0:X]->(anon_1)")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test(
    "Transitive closure inside Exists should still work its magic"
  ) {
    val logicalPlan =
      planner.plan(
        """
          |MATCH (person:Person)
          |WHERE EXISTS {
          |  MATCH (person)-[:HAS_DOG]->(dog:Dog)
          |  WHERE person.name = dog.name AND person.name = 'Bosse' and dog.lastname = person.name
          |}
          |RETURN person.name
      """.stripMargin
      )
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("`person.name`")
        .projection(project = Seq("cacheN[person.name] AS `person.name`"), discard = Set("person"))
        .semiApply()
        .|.filterExpressionOrString("dog:Dog", andsReorderable("dog.name = 'Bosse'", "dog.lastname = 'Bosse'"))
        .|.expandAll("(person)-[anon_0:HAS_DOG]->(dog)")
        .|.filter("cacheNFromStore[person.name] = 'Bosse'")
        .|.argument("person")
        .nodeByLabelScan("person", "Person", IndexOrderNone)
        .build()
    )
  }

  test(
    "should use LetSelectOrAntiSemiApply and LetSelectOrAntiSemiApply to plan two ORed NOT EXISTS ORed with another predicate in RETURN"
  ) {
    val logicalPlan =
      planner.plan(
        "MATCH (a) RETURN NOT EXISTS { (a)-[:X]->(:Foo) } OR NOT EXISTS { (a)-[:X]->(:Bar) } OR a.prop = 10 AS notExists"
      )
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("notExists")
        .projection(
          project = Seq("anon_5 AS notExists"),
          discard = Set("a", "anon_4", "anon_5")
        ) // We could remove this unneeded projection, but it doesn't do much harm
        .letSelectOrAntiSemiApply("anon_5", "anon_4")
        .|.filter("anon_3:Bar")
        .|.expandAll("(a)-[anon_2:X]->(anon_3)")
        .|.argument("a")
        .letSelectOrAntiSemiApply("anon_4", "a.prop = 10")
        .|.filter("anon_1:Foo")
        .|.expandAll("(a)-[anon_0:X]->(anon_1)")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test(
    "should use LetSelectOrAntiSemiApply and LetSelectOrAntiSemiApply to plan two nested ORed NOT EXISTS ORed with another predicate in RETURN"
  ) {
    val logicalPlan =
      planner.plan(
        "MATCH (a) RETURN [NOT EXISTS { (a)-[:X]->(:Foo) } OR NOT EXISTS { (a)-[:X]->(:Bar) } OR a.prop = 10] AS notExists"
      )
    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("notExists")
        .projection(project = Seq("[anon_5] AS notExists"), discard = Set("a", "anon_4", "anon_5"))
        .letSelectOrAntiSemiApply("anon_5", "anon_4")
        .|.filter("anon_3:Bar")
        .|.expandAll("(a)-[anon_2:X]->(anon_3)")
        .|.argument("a")
        .letSelectOrAntiSemiApply("anon_4", "a.prop = 10")
        .|.filter("anon_1:Foo")
        .|.expandAll("(a)-[anon_0:X]->(anon_1)")
        .|.argument("a")
        .allNodeScan("a")
        .build()
    )
  }

  test("should use NestedPlanExpression to plan EXISTS in CASE") {
    val logicalPlan =
      planner.plan("MATCH (a) RETURN CASE a.prop WHEN 1 THEN EXISTS { (a)-[r:X]->(b:Foo) } ELSE false END AS exists")

    val expectedNestedPlan = planner.subPlanBuilder()
      .filter("b:Foo")
      .expand("(a)-[r:X]->(b)")
      .argument("a")
      .build()

    val npeExpression =
      NestedPlanExistsExpression(expectedNestedPlan, s"EXISTS { MATCH (a)-[r:X]->(b)$NL  WHERE b:Foo }")(pos)
    val caseExp = caseExpression(Some(prop("a", "prop")), Some(falseLiteral), literalInt(1) -> npeExpression)

    logicalPlan should equal(
      planner.planBuilder()
        .produceResults("exists")
        .projection(project = Map("exists" -> caseExp), discard = Set("a"))
        .allNodeScan("a")
        .build()
    )
  }

  test("should plan many predicates containing pattern expressions as a single selection") {
    val planner = plannerBuilder()
      .setAllNodesCardinality(100)
      .setAllRelationshipsCardinality(100)
      .setRelationshipCardinality("()-[:REL]-()", 10)
      .build()

    val q =
      """
        |MATCH (a)-->(b)
        |WHERE
        |  (a.name = 'a' AND (a)-[:REL]->(b)) OR
        |  (b.name = 'b' AND (a)-[:REL]->(b)) OR
        |  (a.id = 123 AND (a)-[:REL]->(b)) OR
        |  (b.id = 321 AND (a)-[:REL]->(b)) OR
        |  (a.prop < 321 AND (a)-[:REL]->(b)) OR
        |  (b.prop > 321 AND (a)-[:REL]->(b)) OR
        |  (a.otherProp <> 321 AND (a)-[:REL]->(b))
        |RETURN *
      """.stripMargin

    val plan = planner.plan(q).stripProduceResults
    plan should beLike {
      case Selection(Ands(SetExtractor(Ors(predicates))), _) =>
        predicates.size shouldBe 7
    }
  }

  test("should plan sub-query predicates independently from other predicates") {
    val planner = plannerBuilder()
      .setAllNodesCardinality(171)
      .setLabelCardinality("Person", 133)
      .setLabelCardinality("Movie", 38)
      .setRelationshipCardinality("()-[:ACTED_IN]->()", 172)
      .setRelationshipCardinality("(:Person)-[:ACTED_IN]->()", 172)
      .setRelationshipCardinality("()-[:ACTED_IN]->(:Movie)", 172)
      .setRelationshipCardinality("(:Person)-[:ACTED_IN]->(:Movie)", 172)
      .build()

    val query =
      s"""MATCH (person:Person)
         |WHERE person.name CONTAINS 'i'
         |AND person.born < 1970
         |AND single(x IN [(person)-[:ACTED_IN]->(:Movie {title: 'The Matrix'}) | 1] WHERE true)
         |AND single(x IN [(person)-[:ACTED_IN]->(:Movie {title: 'V for Vendetta', released: 2006}) | 1] WHERE true)
         |RETURN person.name AS name""".stripMargin

    // when
    val plan = planner.plan(query)

    val expected = planner.subPlanBuilder()
      .produceResults("name")
      .projection(project = Seq("cacheN[person.name] AS name"), discard = Set("person", "anon_7", "anon_9"))
      .filter("single(x IN anon_9 WHERE true)")
      .rollUpApply("anon_9", "anon_8")
      .|.projection("1 AS anon_8")
      .|.filterExpressionOrString(
        andsReorderable("anon_3.title = 'V for Vendetta'", "anon_3.released = 2006"),
        "anon_3:Movie"
      )
      .|.expandAll("(person)-[anon_2:ACTED_IN]->(anon_3)")
      .|.argument("person")
      .filter("single(x IN anon_7 WHERE true)")
      .rollUpApply("anon_7", "anon_6")
      .|.projection("1 AS anon_6")
      .|.filter("anon_1.title = 'The Matrix'", "anon_1:Movie")
      .|.expandAll("(person)-[anon_0:ACTED_IN]->(anon_1)")
      .|.argument("person")
      .filter("person.born < 1970", "cacheNFromStore[person.name] CONTAINS 'i'")
      .nodeByLabelScan("person", "Person", IndexOrderNone)
      .build()

    plan shouldEqual expected
  }

  object VariableSet {

    def unapplySeq(s: Set[LogicalVariable]): Option[Seq[String]] = {
      Some(s.toSeq.map(_.name).sorted)
    }
  }
}
