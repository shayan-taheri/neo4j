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
package org.neo4j.cypher.internal.ast.factory.neo4j

import org.neo4j.cypher.internal.ast
import org.neo4j.cypher.internal.ast.Statement
import org.neo4j.cypher.internal.ast.factory.ASTExceptionFactory
import org.neo4j.cypher.internal.ast.factory.ConstraintType
import org.neo4j.cypher.internal.cst.factory.neo4j.AntlrRule
import org.neo4j.cypher.internal.cst.factory.neo4j.Cst
import org.neo4j.cypher.internal.expressions
import org.neo4j.cypher.internal.expressions.AnyTypeName
import org.neo4j.cypher.internal.expressions.BooleanTypeName
import org.neo4j.cypher.internal.expressions.CypherTypeName
import org.neo4j.cypher.internal.expressions.DateTypeName
import org.neo4j.cypher.internal.expressions.DurationTypeName
import org.neo4j.cypher.internal.expressions.FloatTypeName
import org.neo4j.cypher.internal.expressions.IntegerTypeName
import org.neo4j.cypher.internal.expressions.ListTypeName
import org.neo4j.cypher.internal.expressions.LocalDateTimeTypeName
import org.neo4j.cypher.internal.expressions.LocalTimeTypeName
import org.neo4j.cypher.internal.expressions.MapTypeName
import org.neo4j.cypher.internal.expressions.NodeTypeName
import org.neo4j.cypher.internal.expressions.NothingTypeName
import org.neo4j.cypher.internal.expressions.NullTypeName
import org.neo4j.cypher.internal.expressions.PathTypeName
import org.neo4j.cypher.internal.expressions.PointTypeName
import org.neo4j.cypher.internal.expressions.Property
import org.neo4j.cypher.internal.expressions.PropertyKeyName
import org.neo4j.cypher.internal.expressions.PropertyValueTypeName
import org.neo4j.cypher.internal.expressions.RelationshipTypeName
import org.neo4j.cypher.internal.expressions.StringTypeName
import org.neo4j.cypher.internal.expressions.ZonedDateTimeTypeName
import org.neo4j.cypher.internal.expressions.ZonedTimeTypeName
import org.neo4j.cypher.internal.util.symbols.CTMap

/* Tests for creating and dropping constraints */
class ConstraintCommandsParserTest extends AdministrationAndSchemaCommandParserTestBase {

  Seq("ON", "FOR")
    .foreach { forOrOnString =>
      Seq("ASSERT", "REQUIRE")
        .foreach { requireOrAssertString =>
          val containsOn = forOrOnString == "ON"
          val constraintVersion =
            if (requireOrAssertString == "REQUIRE") ast.ConstraintVersion2 else ast.ConstraintVersion0
          val constraintVersionOneOrTwo =
            if (requireOrAssertString == "REQUIRE") ast.ConstraintVersion2 else ast.ConstraintVersion1

          // Create constraint: Without name

          Seq("NODE", "").foreach(nodeKeyword => {
            // Node key

            test(
              s"CREATE CONSTRAINT $forOrOnString (node:Label) $requireOrAssertString (node.prop) IS $nodeKeyword KEY"
            ) {
              yields(ast.CreateNodeKeyConstraint(
                varFor("node"),
                labelName("Label"),
                Seq(prop("node", "prop")),
                None,
                ast.IfExistsThrowError,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT $forOrOnString (node:Label) $requireOrAssertString node.prop IS $nodeKeyword KEY"
            ) {
              yields(ast.CreateNodeKeyConstraint(
                varFor("node"),
                labelName("Label"),
                Seq(prop("node", "prop")),
                None,
                ast.IfExistsThrowError,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE OR REPLACE CONSTRAINT $forOrOnString (node:Label) $requireOrAssertString (node.prop) IS $nodeKeyword KEY"
            ) {
              yields(ast.CreateNodeKeyConstraint(
                varFor("node"),
                labelName("Label"),
                Seq(prop("node", "prop")),
                None,
                ast.IfExistsReplace,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT IF NOT EXISTS $forOrOnString (node:Label) $requireOrAssertString (node.prop) IS $nodeKeyword KEY"
            ) {
              yields(ast.CreateNodeKeyConstraint(
                varFor("node"),
                labelName("Label"),
                Seq(prop("node", "prop")),
                None,
                ast.IfExistsDoNothing,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT $forOrOnString (node:Label) $requireOrAssertString (node.prop1,node.prop2) IS $nodeKeyword KEY"
            ) {
              yields(ast.CreateNodeKeyConstraint(
                varFor("node"),
                labelName("Label"),
                Seq(prop("node", "prop1"), prop("node", "prop2")),
                None,
                ast.IfExistsThrowError,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE OR REPLACE CONSTRAINT IF NOT EXISTS $forOrOnString (node:Label) $requireOrAssertString (node.prop1,node.prop2) IS $nodeKeyword KEY"
            ) {
              yields(ast.CreateNodeKeyConstraint(
                varFor("node"),
                labelName("Label"),
                Seq(prop("node", "prop1"), prop("node", "prop2")),
                None,
                ast.IfExistsInvalidSyntax,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT $forOrOnString (node:Label) $requireOrAssertString (node.prop) IS $nodeKeyword KEY OPTIONS {indexProvider : 'range-1.0'}"
            ) {
              yields(ast.CreateNodeKeyConstraint(
                varFor("node"),
                labelName("Label"),
                Seq(prop("node", "prop")),
                None,
                ast.IfExistsThrowError,
                ast.OptionsMap(Map("indexProvider" -> literalString("range-1.0"))),
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT $forOrOnString (node:Label) $requireOrAssertString (node.prop) IS $nodeKeyword KEY OPTIONS {indexProvider : 'native-btree-1.0', indexConfig : {`spatial.cartesian.max`: [100.0,100.0], `spatial.cartesian.min`: [-100.0,-100.0] }}"
            ) {
              // will fail in options converter
              yields(ast.CreateNodeKeyConstraint(
                varFor("node"),
                labelName("Label"),
                Seq(prop("node", "prop")),
                None,
                ast.IfExistsThrowError,
                ast.OptionsMap(Map(
                  "indexProvider" -> literalString("native-btree-1.0"),
                  "indexConfig" -> mapOf(
                    "spatial.cartesian.max" -> listOf(literalFloat(100.0), literalFloat(100.0)),
                    "spatial.cartesian.min" -> listOf(literalFloat(-100.0), literalFloat(-100.0))
                  )
                )),
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT $forOrOnString (node:Label) $requireOrAssertString (node.prop) IS $nodeKeyword KEY OPTIONS {indexConfig : {someConfig: 'toShowItCanBeParsed' }}"
            ) {
              yields(ast.CreateNodeKeyConstraint(
                varFor("node"),
                labelName("Label"),
                Seq(prop("node", "prop")),
                None,
                ast.IfExistsThrowError,
                ast.OptionsMap(Map("indexConfig" -> mapOf("someConfig" -> literalString("toShowItCanBeParsed")))),
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT $forOrOnString (node:Label) $requireOrAssertString (node.prop) IS $nodeKeyword KEY OPTIONS {nonValidOption : 42}"
            ) {
              yields(ast.CreateNodeKeyConstraint(
                varFor("node"),
                labelName("Label"),
                Seq(prop("node", "prop")),
                None,
                ast.IfExistsThrowError,
                ast.OptionsMap(Map("nonValidOption" -> literalInt(42))),
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT $forOrOnString (node:Label) $requireOrAssertString (node.prop) IS $nodeKeyword KEY OPTIONS {}"
            ) {
              yields(ast.CreateNodeKeyConstraint(
                varFor("node"),
                labelName("Label"),
                Seq(prop("node", "prop")),
                None,
                ast.IfExistsThrowError,
                ast.OptionsMap(Map.empty),
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT $forOrOnString (node:Label) $requireOrAssertString (node.prop) IS $nodeKeyword KEY OPTIONS $$param"
            ) {
              yields(ast.CreateNodeKeyConstraint(
                varFor("node"),
                labelName("Label"),
                Seq(prop("node", "prop")),
                None,
                ast.IfExistsThrowError,
                ast.OptionsParam(parameter("param", CTMap)),
                containsOn,
                constraintVersion
              ))
            }

            // Node uniqueness

            test(
              s"CREATE CONSTRAINT $forOrOnString (node:Label) $requireOrAssertString node.prop IS $nodeKeyword UNIQUE"
            ) {
              yields(ast.CreateNodePropertyUniquenessConstraint(
                varFor("node"),
                labelName("Label"),
                Seq(prop("node", "prop")),
                None,
                ast.IfExistsThrowError,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE OR REPLACE CONSTRAINT $forOrOnString (node:Label) $requireOrAssertString node.prop IS $nodeKeyword UNIQUE"
            ) {
              yields(ast.CreateNodePropertyUniquenessConstraint(
                varFor("node"),
                labelName("Label"),
                Seq(prop("node", "prop")),
                None,
                ast.IfExistsReplace,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT IF NOT EXISTS $forOrOnString (node:Label) $requireOrAssertString node.prop IS $nodeKeyword UNIQUE"
            ) {
              yields(ast.CreateNodePropertyUniquenessConstraint(
                varFor("node"),
                labelName("Label"),
                Seq(prop("node", "prop")),
                None,
                ast.IfExistsDoNothing,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT $forOrOnString (node:Label) $requireOrAssertString (node.prop) IS $nodeKeyword UNIQUE"
            ) {
              yields(ast.CreateNodePropertyUniquenessConstraint(
                varFor("node"),
                labelName("Label"),
                Seq(prop("node", "prop")),
                None,
                ast.IfExistsThrowError,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT $forOrOnString (node:Label) $requireOrAssertString (node.prop1,node.prop2) IS $nodeKeyword UNIQUE"
            ) {
              yields(ast.CreateNodePropertyUniquenessConstraint(
                varFor("node"),
                labelName("Label"),
                Seq(prop("node", "prop1"), prop("node", "prop2")),
                None,
                ast.IfExistsThrowError,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE OR REPLACE CONSTRAINT IF NOT EXISTS $forOrOnString (node:Label) $requireOrAssertString (node.prop1,node.prop2) IS $nodeKeyword UNIQUE"
            ) {
              yields(ast.CreateNodePropertyUniquenessConstraint(
                varFor("node"),
                labelName("Label"),
                Seq(prop("node", "prop1"), prop("node", "prop2")),
                None,
                ast.IfExistsInvalidSyntax,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT $forOrOnString (node:Label) $requireOrAssertString (node.prop) IS $nodeKeyword UNIQUE OPTIONS {indexConfig : {`spatial.wgs-84.max`: [60.0,60.0], `spatial.wgs-84.min`: [-40.0,-40.0]}, indexProvider : 'native-btree-1.0'}"
            ) {
              // will fail in options converter
              yields(ast.CreateNodePropertyUniquenessConstraint(
                varFor("node"),
                labelName("Label"),
                Seq(prop("node", "prop")),
                None,
                ast.IfExistsThrowError,
                ast.OptionsMap(Map(
                  "indexProvider" -> literalString("native-btree-1.0"),
                  "indexConfig" -> mapOf(
                    "spatial.wgs-84.max" -> listOf(literalFloat(60.0), literalFloat(60.0)),
                    "spatial.wgs-84.min" -> listOf(literalFloat(-40.0), literalFloat(-40.0))
                  )
                )),
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT $forOrOnString (node:Label) $requireOrAssertString (node.prop) IS $nodeKeyword UNIQUE OPTIONS $$options"
            ) {
              yields(ast.CreateNodePropertyUniquenessConstraint(
                varFor("node"),
                labelName("Label"),
                Seq(prop("node", "prop")),
                None,
                ast.IfExistsThrowError,
                ast.OptionsParam(parameter("options", CTMap)),
                containsOn,
                constraintVersion
              ))
            }
          })

          Seq("RELATIONSHIP", "REL", "").foreach(relKeyWord => {
            // Relationship key

            test(s"CREATE CONSTRAINT $forOrOnString ()-[r1:R]-() $requireOrAssertString (r2.prop) IS $relKeyWord KEY") {
              yields(ast.CreateRelationshipKeyConstraint(
                varFor("r1"),
                relTypeName("R"),
                Seq(prop("r2", "prop")),
                None,
                ast.IfExistsThrowError,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT $forOrOnString ()-[r1:R]->() $requireOrAssertString (r2.prop1, r3.prop2) IS $relKeyWord KEY"
            ) {
              yields(ast.CreateRelationshipKeyConstraint(
                varFor("r1"),
                relTypeName("R"),
                Seq(prop("r2", "prop1"), prop("r3", "prop2")),
                None,
                ast.IfExistsThrowError,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT $forOrOnString ()<-[r1:R]-() $requireOrAssertString (r2.prop) IS $relKeyWord KEY"
            ) {
              yields(ast.CreateRelationshipKeyConstraint(
                varFor("r1"),
                relTypeName("R"),
                Seq(prop("r2", "prop")),
                None,
                ast.IfExistsThrowError,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT $forOrOnString ()<-[r1:R]->() $requireOrAssertString (r2.prop) IS $relKeyWord KEY"
            ) {
              yields(ast.CreateRelationshipKeyConstraint(
                varFor("r1"),
                relTypeName("R"),
                Seq(prop("r2", "prop")),
                None,
                ast.IfExistsThrowError,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE OR REPLACE CONSTRAINT $forOrOnString ()-[r1:R]-() $requireOrAssertString (r2.prop) IS $relKeyWord KEY"
            ) {
              yields(ast.CreateRelationshipKeyConstraint(
                varFor("r1"),
                relTypeName("R"),
                Seq(prop("r2", "prop")),
                None,
                ast.IfExistsReplace,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE OR REPLACE CONSTRAINT IF NOT EXISTS $forOrOnString ()-[r1:R]-() $requireOrAssertString (r2.prop) IS $relKeyWord KEY"
            ) {
              yields(ast.CreateRelationshipKeyConstraint(
                varFor("r1"),
                relTypeName("R"),
                Seq(prop("r2", "prop")),
                None,
                ast.IfExistsInvalidSyntax,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT IF NOT EXISTS $forOrOnString ()-[r1:R]-() $requireOrAssertString (r2.prop) IS $relKeyWord KEY"
            ) {
              yields(ast.CreateRelationshipKeyConstraint(
                varFor("r1"),
                relTypeName("R"),
                Seq(prop("r2", "prop")),
                None,
                ast.IfExistsDoNothing,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT $forOrOnString ()-[r1:R]-() $requireOrAssertString (r2.prop) IS $relKeyWord KEY OPTIONS {key: 'value'}"
            ) {
              yields(ast.CreateRelationshipKeyConstraint(
                varFor("r1"),
                relTypeName("R"),
                Seq(prop("r2", "prop")),
                None,
                ast.IfExistsThrowError,
                ast.OptionsMap(Map("key" -> literalString("value"))),
                containsOn,
                constraintVersion
              ))
            }

            // Relationship uniqueness

            test(
              s"CREATE CONSTRAINT $forOrOnString ()-[r1:R]-() $requireOrAssertString (r2.prop) IS $relKeyWord UNIQUE"
            ) {
              yields(ast.CreateRelationshipPropertyUniquenessConstraint(
                varFor("r1"),
                relTypeName("R"),
                Seq(prop("r2", "prop")),
                None,
                ast.IfExistsThrowError,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT $forOrOnString ()-[r1:R]->() $requireOrAssertString (r2.prop1, r3.prop2) IS $relKeyWord UNIQUE"
            ) {
              yields(ast.CreateRelationshipPropertyUniquenessConstraint(
                varFor("r1"),
                relTypeName("R"),
                Seq(prop("r2", "prop1"), prop("r3", "prop2")),
                None,
                ast.IfExistsThrowError,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT $forOrOnString ()<-[r1:R]-() $requireOrAssertString (r2.prop) IS $relKeyWord UNIQUE"
            ) {
              yields(ast.CreateRelationshipPropertyUniquenessConstraint(
                varFor("r1"),
                relTypeName("R"),
                Seq(prop("r2", "prop")),
                None,
                ast.IfExistsThrowError,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT $forOrOnString ()<-[r1:R]->() $requireOrAssertString (r2.prop) IS $relKeyWord UNIQUE"
            ) {
              yields(ast.CreateRelationshipPropertyUniquenessConstraint(
                varFor("r1"),
                relTypeName("R"),
                Seq(prop("r2", "prop")),
                None,
                ast.IfExistsThrowError,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE OR REPLACE CONSTRAINT $forOrOnString ()-[r1:R]-() $requireOrAssertString (r2.prop) IS $relKeyWord UNIQUE"
            ) {
              yields(ast.CreateRelationshipPropertyUniquenessConstraint(
                varFor("r1"),
                relTypeName("R"),
                Seq(prop("r2", "prop")),
                None,
                ast.IfExistsReplace,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE OR REPLACE CONSTRAINT IF NOT EXISTS $forOrOnString ()-[r1:R]-() $requireOrAssertString (r2.prop) IS $relKeyWord UNIQUE"
            ) {
              yields(ast.CreateRelationshipPropertyUniquenessConstraint(
                varFor("r1"),
                relTypeName("R"),
                Seq(prop("r2", "prop")),
                None,
                ast.IfExistsInvalidSyntax,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT IF NOT EXISTS $forOrOnString ()-[r1:R]-() $requireOrAssertString (r2.prop) IS $relKeyWord UNIQUE"
            ) {
              yields(ast.CreateRelationshipPropertyUniquenessConstraint(
                varFor("r1"),
                relTypeName("R"),
                Seq(prop("r2", "prop")),
                None,
                ast.IfExistsDoNothing,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT $forOrOnString ()-[r1:R]-() $requireOrAssertString (r2.prop) IS $relKeyWord UNIQUE OPTIONS {key: 'value'}"
            ) {
              yields(ast.CreateRelationshipPropertyUniquenessConstraint(
                varFor("r1"),
                relTypeName("R"),
                Seq(prop("r2", "prop")),
                None,
                ast.IfExistsThrowError,
                ast.OptionsMap(Map("key" -> literalString("value"))),
                containsOn,
                constraintVersion
              ))
            }
          })

          // Node property existence

          test(s"CREATE CONSTRAINT $forOrOnString (node:Label) $requireOrAssertString node.prop IS NOT NULL") {
            yields(ast.CreateNodePropertyExistenceConstraint(
              varFor("node"),
              labelName("Label"),
              prop("node", "prop"),
              None,
              ast.IfExistsThrowError,
              ast.NoOptions,
              containsOn,
              constraintVersionOneOrTwo
            ))
          }

          test(s"CREATE CONSTRAINT $forOrOnString (node:Label) $requireOrAssertString (node.prop) IS NOT NULL") {
            yields(ast.CreateNodePropertyExistenceConstraint(
              varFor("node"),
              labelName("Label"),
              prop("node", "prop"),
              None,
              ast.IfExistsThrowError,
              ast.NoOptions,
              containsOn,
              constraintVersionOneOrTwo
            ))
          }

          test(
            s"CREATE OR REPLACE CONSTRAINT $forOrOnString (node:Label) $requireOrAssertString node.prop IS NOT NULL"
          ) {
            yields(ast.CreateNodePropertyExistenceConstraint(
              varFor("node"),
              labelName("Label"),
              prop("node", "prop"),
              None,
              ast.IfExistsReplace,
              ast.NoOptions,
              containsOn,
              constraintVersionOneOrTwo
            ))
          }

          test(
            s"CREATE OR REPLACE CONSTRAINT IF NOT EXISTS $forOrOnString (node:Label) $requireOrAssertString node.prop IS NOT NULL"
          ) {
            yields(ast.CreateNodePropertyExistenceConstraint(
              varFor("node"),
              labelName("Label"),
              prop("node", "prop"),
              None,
              ast.IfExistsInvalidSyntax,
              ast.NoOptions,
              containsOn,
              constraintVersionOneOrTwo
            ))
          }

          test(
            s"CREATE CONSTRAINT IF NOT EXISTS $forOrOnString (node:Label) $requireOrAssertString (node.prop) IS NOT NULL"
          ) {
            yields(ast.CreateNodePropertyExistenceConstraint(
              varFor("node"),
              labelName("Label"),
              prop("node", "prop"),
              None,
              ast.IfExistsDoNothing,
              ast.NoOptions,
              containsOn,
              constraintVersionOneOrTwo
            ))
          }

          // Relationship property existence

          test(s"CREATE CONSTRAINT $forOrOnString ()-[r:R]-() $requireOrAssertString r.prop IS NOT NULL") {
            yields(ast.CreateRelationshipPropertyExistenceConstraint(
              varFor("r"),
              relTypeName("R"),
              prop("r", "prop"),
              None,
              ast.IfExistsThrowError,
              ast.NoOptions,
              containsOn,
              constraintVersionOneOrTwo
            ))
          }

          test(s"CREATE CONSTRAINT $forOrOnString ()-[r:R]->() $requireOrAssertString r.prop IS NOT NULL") {
            yields(ast.CreateRelationshipPropertyExistenceConstraint(
              varFor("r"),
              relTypeName("R"),
              prop("r", "prop"),
              None,
              ast.IfExistsThrowError,
              ast.NoOptions,
              containsOn,
              constraintVersionOneOrTwo
            ))
          }

          test(s"CREATE CONSTRAINT $forOrOnString ()<-[r:R]-() $requireOrAssertString (r.prop) IS NOT NULL") {
            yields(ast.CreateRelationshipPropertyExistenceConstraint(
              varFor("r"),
              relTypeName("R"),
              prop("r", "prop"),
              None,
              ast.IfExistsThrowError,
              ast.NoOptions,
              containsOn,
              constraintVersionOneOrTwo
            ))
          }

          test(s"CREATE CONSTRAINT $forOrOnString ()<-[r:R]->() $requireOrAssertString (r.prop) IS NOT NULL") {
            yields(ast.CreateRelationshipPropertyExistenceConstraint(
              varFor("r"),
              relTypeName("R"),
              prop("r", "prop"),
              None,
              ast.IfExistsThrowError,
              ast.NoOptions,
              containsOn,
              constraintVersionOneOrTwo
            ))
          }

          test(s"CREATE OR REPLACE CONSTRAINT $forOrOnString ()<-[r:R]-() $requireOrAssertString r.prop IS NOT NULL") {
            yields(ast.CreateRelationshipPropertyExistenceConstraint(
              varFor("r"),
              relTypeName("R"),
              prop("r", "prop"),
              None,
              ast.IfExistsReplace,
              ast.NoOptions,
              containsOn,
              constraintVersionOneOrTwo
            ))
          }

          test(
            s"CREATE OR REPLACE CONSTRAINT IF NOT EXISTS $forOrOnString ()-[r:R]-() $requireOrAssertString r.prop IS NOT NULL"
          ) {
            yields(ast.CreateRelationshipPropertyExistenceConstraint(
              varFor("r"),
              relTypeName("R"),
              prop("r", "prop"),
              None,
              ast.IfExistsInvalidSyntax,
              ast.NoOptions,
              containsOn,
              constraintVersionOneOrTwo
            ))
          }

          test(
            s"CREATE CONSTRAINT IF NOT EXISTS $forOrOnString ()-[r:R]->() $requireOrAssertString r.prop IS NOT NULL"
          ) {
            yields(ast.CreateRelationshipPropertyExistenceConstraint(
              varFor("r"),
              relTypeName("R"),
              prop("r", "prop"),
              None,
              ast.IfExistsDoNothing,
              ast.NoOptions,
              containsOn,
              constraintVersionOneOrTwo
            ))
          }

          test(s"CREATE CONSTRAINT $forOrOnString ()-[r:R]-() $requireOrAssertString (r.prop) IS NOT NULL OPTIONS {}") {
            yields(ast.CreateRelationshipPropertyExistenceConstraint(
              varFor("r"),
              relTypeName("R"),
              prop("r", "prop"),
              None,
              ast.IfExistsThrowError,
              ast.OptionsMap(Map.empty),
              containsOn,
              constraintVersionOneOrTwo
            ))
          }

          Seq("IS TYPED", "IS ::", "::").foreach(typeKeyword => {

            // Node property type

            test(
              s"CREATE CONSTRAINT $forOrOnString (node:Label) $requireOrAssertString node.prop $typeKeyword BOOLEAN"
            ) {
              yields(ast.CreateNodePropertyTypeConstraint(
                varFor("node"),
                labelName("Label"),
                prop("node", "prop"),
                expressions.BooleanTypeName(true),
                None,
                ast.IfExistsThrowError,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT $forOrOnString (node:Label) $requireOrAssertString (node.prop) $typeKeyword BOOLEAN"
            ) {
              yields(ast.CreateNodePropertyTypeConstraint(
                varFor("node"),
                labelName("Label"),
                prop("node", "prop"),
                expressions.BooleanTypeName(true),
                None,
                ast.IfExistsThrowError,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE OR REPLACE CONSTRAINT $forOrOnString (node:Label) $requireOrAssertString node.prop $typeKeyword BOOLEAN"
            ) {
              yields(ast.CreateNodePropertyTypeConstraint(
                varFor("node"),
                labelName("Label"),
                prop("node", "prop"),
                expressions.BooleanTypeName(true),
                None,
                ast.IfExistsReplace,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE OR REPLACE CONSTRAINT IF NOT EXISTS $forOrOnString (node:Label) $requireOrAssertString node.prop $typeKeyword BOOLEAN"
            ) {
              yields(ast.CreateNodePropertyTypeConstraint(
                varFor("node"),
                labelName("Label"),
                prop("node", "prop"),
                expressions.BooleanTypeName(true),
                None,
                ast.IfExistsInvalidSyntax,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT IF NOT EXISTS $forOrOnString (node:Label) $requireOrAssertString (node.prop) $typeKeyword BOOLEAN"
            ) {
              yields(ast.CreateNodePropertyTypeConstraint(
                varFor("node"),
                labelName("Label"),
                prop("node", "prop"),
                expressions.BooleanTypeName(true),
                None,
                ast.IfExistsDoNothing,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            // Relationship property type

            test(s"CREATE CONSTRAINT $forOrOnString ()-[r:R]-() $requireOrAssertString r.prop $typeKeyword BOOLEAN") {
              yields(ast.CreateRelationshipPropertyTypeConstraint(
                varFor("r"),
                relTypeName("R"),
                prop("r", "prop"),
                expressions.BooleanTypeName(true),
                None,
                ast.IfExistsThrowError,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(s"CREATE CONSTRAINT $forOrOnString ()-[r:R]->() $requireOrAssertString r.prop $typeKeyword BOOLEAN") {
              yields(ast.CreateRelationshipPropertyTypeConstraint(
                varFor("r"),
                relTypeName("R"),
                prop("r", "prop"),
                expressions.BooleanTypeName(true),
                None,
                ast.IfExistsThrowError,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT $forOrOnString ()<-[r:R]-() $requireOrAssertString (r.prop) $typeKeyword BOOLEAN"
            ) {
              yields(ast.CreateRelationshipPropertyTypeConstraint(
                varFor("r"),
                relTypeName("R"),
                prop("r", "prop"),
                expressions.BooleanTypeName(true),
                None,
                ast.IfExistsThrowError,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT $forOrOnString ()<-[r:R]->() $requireOrAssertString (r.prop) $typeKeyword BOOLEAN"
            ) {
              yields(ast.CreateRelationshipPropertyTypeConstraint(
                varFor("r"),
                relTypeName("R"),
                prop("r", "prop"),
                expressions.BooleanTypeName(true),
                None,
                ast.IfExistsThrowError,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE OR REPLACE CONSTRAINT $forOrOnString ()<-[r:R]-() $requireOrAssertString r.prop $typeKeyword BOOLEAN"
            ) {
              yields(ast.CreateRelationshipPropertyTypeConstraint(
                varFor("r"),
                relTypeName("R"),
                prop("r", "prop"),
                expressions.BooleanTypeName(true),
                None,
                ast.IfExistsReplace,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE OR REPLACE CONSTRAINT IF NOT EXISTS $forOrOnString ()-[r:R]-() $requireOrAssertString r.prop $typeKeyword BOOLEAN"
            ) {
              yields(ast.CreateRelationshipPropertyTypeConstraint(
                varFor("r"),
                relTypeName("R"),
                prop("r", "prop"),
                expressions.BooleanTypeName(true),
                None,
                ast.IfExistsInvalidSyntax,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT IF NOT EXISTS $forOrOnString ()-[r:R]->() $requireOrAssertString r.prop $typeKeyword BOOLEAN"
            ) {
              yields(ast.CreateRelationshipPropertyTypeConstraint(
                varFor("r"),
                relTypeName("R"),
                prop("r", "prop"),
                expressions.BooleanTypeName(true),
                None,
                ast.IfExistsDoNothing,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT $forOrOnString ()-[r:R]-() $requireOrAssertString (r.prop) $typeKeyword BOOLEAN OPTIONS {}"
            ) {
              yields(ast.CreateRelationshipPropertyTypeConstraint(
                varFor("r"),
                relTypeName("R"),
                prop("r", "prop"),
                expressions.BooleanTypeName(true),
                None,
                ast.IfExistsThrowError,
                ast.OptionsMap(Map.empty),
                containsOn,
                constraintVersion
              ))
            }
          })

          // Create constraint: With name

          Seq("NODE", "").foreach(nodeKeyword => {
            // Node key
            test(
              s"USE neo4j CREATE CONSTRAINT my_constraint $forOrOnString (node:Label) $requireOrAssertString (node.prop) IS $nodeKeyword KEY"
            ) {
              yields(ast.CreateNodeKeyConstraint(
                varFor("node"),
                labelName("Label"),
                Seq(prop("node", "prop")),
                Some("my_constraint"),
                ast.IfExistsThrowError,
                ast.NoOptions,
                containsOn,
                constraintVersion,
                Some(use(varFor("neo4j")))
              ))
            }

            test(
              s"USE neo4j CREATE OR REPLACE CONSTRAINT my_constraint IF NOT EXISTS $forOrOnString (node:Label) $requireOrAssertString (node.prop) IS $nodeKeyword KEY"
            ) {
              yields(ast.CreateNodeKeyConstraint(
                varFor("node"),
                labelName("Label"),
                Seq(prop("node", "prop")),
                Some("my_constraint"),
                ast.IfExistsInvalidSyntax,
                ast.NoOptions,
                containsOn,
                constraintVersion,
                Some(use(varFor("neo4j")))
              ))
            }

            test(
              s"CREATE CONSTRAINT my_constraint $forOrOnString (node:Label) $requireOrAssertString (node.prop1,node.prop2) IS $nodeKeyword KEY"
            ) {
              yields(ast.CreateNodeKeyConstraint(
                varFor("node"),
                labelName("Label"),
                Seq(prop("node", "prop1"), prop("node", "prop2")),
                Some("my_constraint"),
                ast.IfExistsThrowError,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE OR REPLACE CONSTRAINT my_constraint $forOrOnString (node:Label) $requireOrAssertString (node.prop1,node.prop2) IS $nodeKeyword KEY"
            ) {
              yields(ast.CreateNodeKeyConstraint(
                varFor("node"),
                labelName("Label"),
                Seq(prop("node", "prop1"), prop("node", "prop2")),
                Some("my_constraint"),
                ast.IfExistsReplace,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT my_constraint IF NOT EXISTS $forOrOnString (node:Label) $requireOrAssertString (node.prop1,node.prop2) IS $nodeKeyword KEY"
            ) {
              yields(ast.CreateNodeKeyConstraint(
                varFor("node"),
                labelName("Label"),
                Seq(prop("node", "prop1"), prop("node", "prop2")),
                Some("my_constraint"),
                ast.IfExistsDoNothing,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT my_constraint $forOrOnString (node:Label) $requireOrAssertString (node.prop) IS $nodeKeyword KEY OPTIONS {indexProvider : 'native-btree-1.0', indexConfig : {`spatial.wgs-84.max`: [60.0,60.0], `spatial.wgs-84.min`: [-40.0,-40.0]}}"
            ) {
              // will fail in options converter
              yields(ast.CreateNodeKeyConstraint(
                varFor("node"),
                labelName("Label"),
                Seq(prop("node", "prop")),
                Some("my_constraint"),
                ast.IfExistsThrowError,
                ast.OptionsMap(Map(
                  "indexProvider" -> literalString("native-btree-1.0"),
                  "indexConfig" -> mapOf(
                    "spatial.wgs-84.max" -> listOf(literalFloat(60.0), literalFloat(60.0)),
                    "spatial.wgs-84.min" -> listOf(literalFloat(-40.0), literalFloat(-40.0))
                  )
                )),
                containsOn,
                constraintVersion
              ))
            }

            // Node uniqueness

            test(
              s"CREATE CONSTRAINT my_constraint $forOrOnString (node:Label) $requireOrAssertString node.prop IS $nodeKeyword UNIQUE"
            ) {
              yields(ast.CreateNodePropertyUniquenessConstraint(
                varFor("node"),
                labelName("Label"),
                Seq(prop("node", "prop")),
                Some("my_constraint"),
                ast.IfExistsThrowError,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT my_constraint $forOrOnString (node:Label) $requireOrAssertString (node.prop) IS $nodeKeyword UNIQUE"
            ) {
              yields(ast.CreateNodePropertyUniquenessConstraint(
                varFor("node"),
                labelName("Label"),
                Seq(prop("node", "prop")),
                Some("my_constraint"),
                ast.IfExistsThrowError,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE OR REPLACE CONSTRAINT my_constraint IF NOT EXISTS $forOrOnString (node:Label) $requireOrAssertString (node.prop) IS $nodeKeyword UNIQUE"
            ) {
              yields(ast.CreateNodePropertyUniquenessConstraint(
                varFor("node"),
                labelName("Label"),
                Seq(prop("node", "prop")),
                Some("my_constraint"),
                ast.IfExistsInvalidSyntax,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT my_constraint $forOrOnString (node:Label) $requireOrAssertString (node.prop1,node.prop2) IS $nodeKeyword UNIQUE"
            ) {
              yields(ast.CreateNodePropertyUniquenessConstraint(
                varFor("node"),
                labelName("Label"),
                Seq(prop("node", "prop1"), prop("node", "prop2")),
                Some("my_constraint"),
                ast.IfExistsThrowError,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE OR REPLACE CONSTRAINT my_constraint $forOrOnString (node:Label) $requireOrAssertString (node.prop1,node.prop2) IS $nodeKeyword UNIQUE"
            ) {
              yields(ast.CreateNodePropertyUniquenessConstraint(
                varFor("node"),
                labelName("Label"),
                Seq(prop("node", "prop1"), prop("node", "prop2")),
                Some("my_constraint"),
                ast.IfExistsReplace,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT my_constraint IF NOT EXISTS $forOrOnString (node:Label) $requireOrAssertString (node.prop1,node.prop2) IS $nodeKeyword UNIQUE"
            ) {
              yields(ast.CreateNodePropertyUniquenessConstraint(
                varFor("node"),
                labelName("Label"),
                Seq(prop("node", "prop1"), prop("node", "prop2")),
                Some("my_constraint"),
                ast.IfExistsDoNothing,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT my_constraint $forOrOnString (node:Label) $requireOrAssertString (node.prop) IS $nodeKeyword UNIQUE OPTIONS {indexProvider : 'range-1.0'}"
            ) {
              yields(ast.CreateNodePropertyUniquenessConstraint(
                varFor("node"),
                labelName("Label"),
                Seq(prop("node", "prop")),
                Some("my_constraint"),
                ast.IfExistsThrowError,
                ast.OptionsMap(Map("indexProvider" -> literalString("range-1.0"))),
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT my_constraint $forOrOnString (node:Label) $requireOrAssertString (node.prop) IS $nodeKeyword UNIQUE OPTIONS {indexProvider : 'native-btree-1.0', indexConfig : {`spatial.cartesian.max`: [100.0,100.0], `spatial.cartesian.min`: [-100.0,-100.0] }}"
            ) {
              // will fail in options converter
              yields(ast.CreateNodePropertyUniquenessConstraint(
                varFor("node"),
                labelName("Label"),
                Seq(prop("node", "prop")),
                Some("my_constraint"),
                ast.IfExistsThrowError,
                ast.OptionsMap(Map(
                  "indexProvider" -> literalString("native-btree-1.0"),
                  "indexConfig" -> mapOf(
                    "spatial.cartesian.max" -> listOf(literalFloat(100.0), literalFloat(100.0)),
                    "spatial.cartesian.min" -> listOf(literalFloat(-100.0), literalFloat(-100.0))
                  )
                )),
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT my_constraint $forOrOnString (node:Label) $requireOrAssertString (node.prop) IS $nodeKeyword UNIQUE OPTIONS {indexConfig : {someConfig: 'toShowItCanBeParsed' }}"
            ) {
              yields(ast.CreateNodePropertyUniquenessConstraint(
                varFor("node"),
                labelName("Label"),
                Seq(prop("node", "prop")),
                Some("my_constraint"),
                ast.IfExistsThrowError,
                ast.OptionsMap(Map("indexConfig" -> mapOf("someConfig" -> literalString("toShowItCanBeParsed")))),
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT my_constraint $forOrOnString (node:Label) $requireOrAssertString (node.prop) IS $nodeKeyword UNIQUE OPTIONS {nonValidOption : 42}"
            ) {
              yields(ast.CreateNodePropertyUniquenessConstraint(
                varFor("node"),
                labelName("Label"),
                Seq(prop("node", "prop")),
                Some("my_constraint"),
                ast.IfExistsThrowError,
                ast.OptionsMap(Map("nonValidOption" -> literalInt(42))),
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT my_constraint $forOrOnString (node:Label) $requireOrAssertString (node.prop1,node.prop2) IS $nodeKeyword UNIQUE OPTIONS {}"
            ) {
              yields(ast.CreateNodePropertyUniquenessConstraint(
                varFor("node"),
                labelName("Label"),
                Seq(prop("node", "prop1"), prop("node", "prop2")),
                Some("my_constraint"),
                ast.IfExistsThrowError,
                ast.OptionsMap(Map.empty),
                containsOn,
                constraintVersion
              ))
            }
          })

          Seq("RELATIONSHIP", "REL", "").foreach(relKeyWord => {
            // Relationship key

            test(
              s"CREATE CONSTRAINT my_constraint $forOrOnString ()-[r1:R]-() $requireOrAssertString (r2.prop) IS $relKeyWord KEY"
            ) {
              yields(ast.CreateRelationshipKeyConstraint(
                varFor("r1"),
                relTypeName("R"),
                Seq(prop("r2", "prop")),
                Some("my_constraint"),
                ast.IfExistsThrowError,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT my_constraint $forOrOnString ()-[r1:R]->() $requireOrAssertString (r2.prop1, r3.prop2) IS $relKeyWord KEY"
            ) {
              yields(ast.CreateRelationshipKeyConstraint(
                varFor("r1"),
                relTypeName("R"),
                Seq(prop("r2", "prop1"), prop("r3", "prop2")),
                Some("my_constraint"),
                ast.IfExistsThrowError,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT my_constraint $forOrOnString ()<-[r1:R]-() $requireOrAssertString (r2.prop) IS $relKeyWord KEY"
            ) {
              yields(ast.CreateRelationshipKeyConstraint(
                varFor("r1"),
                relTypeName("R"),
                Seq(prop("r2", "prop")),
                Some("my_constraint"),
                ast.IfExistsThrowError,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT my_constraint $forOrOnString ()<-[r1:R]->() $requireOrAssertString (r2.prop) IS $relKeyWord KEY"
            ) {
              yields(ast.CreateRelationshipKeyConstraint(
                varFor("r1"),
                relTypeName("R"),
                Seq(prop("r2", "prop")),
                Some("my_constraint"),
                ast.IfExistsThrowError,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE OR REPLACE CONSTRAINT my_constraint $forOrOnString ()-[r1:R]-() $requireOrAssertString (r2.prop) IS $relKeyWord KEY"
            ) {
              yields(ast.CreateRelationshipKeyConstraint(
                varFor("r1"),
                relTypeName("R"),
                Seq(prop("r2", "prop")),
                Some("my_constraint"),
                ast.IfExistsReplace,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE OR REPLACE CONSTRAINT my_constraint IF NOT EXISTS $forOrOnString ()-[r1:R]-() $requireOrAssertString (r2.prop) IS $relKeyWord KEY"
            ) {
              yields(ast.CreateRelationshipKeyConstraint(
                varFor("r1"),
                relTypeName("R"),
                Seq(prop("r2", "prop")),
                Some("my_constraint"),
                ast.IfExistsInvalidSyntax,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT my_constraint IF NOT EXISTS $forOrOnString ()-[r1:R]-() $requireOrAssertString (r2.prop) IS $relKeyWord KEY"
            ) {
              yields(ast.CreateRelationshipKeyConstraint(
                varFor("r1"),
                relTypeName("R"),
                Seq(prop("r2", "prop")),
                Some("my_constraint"),
                ast.IfExistsDoNothing,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT my_constraint $forOrOnString ()-[r1:R]-() $requireOrAssertString (r2.prop) IS $relKeyWord KEY OPTIONS {key: 'value'}"
            ) {
              yields(ast.CreateRelationshipKeyConstraint(
                varFor("r1"),
                relTypeName("R"),
                Seq(prop("r2", "prop")),
                Some("my_constraint"),
                ast.IfExistsThrowError,
                ast.OptionsMap(Map("key" -> literalString("value"))),
                containsOn,
                constraintVersion
              ))
            }

            // Relationship uniqueness

            test(
              s"CREATE CONSTRAINT my_constraint $forOrOnString ()-[r1:R]-() $requireOrAssertString (r2.prop) IS $relKeyWord UNIQUE"
            ) {
              yields(ast.CreateRelationshipPropertyUniquenessConstraint(
                varFor("r1"),
                relTypeName("R"),
                Seq(prop("r2", "prop")),
                Some("my_constraint"),
                ast.IfExistsThrowError,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT my_constraint $forOrOnString ()-[r1:R]->() $requireOrAssertString (r2.prop1, r3.prop2) IS $relKeyWord UNIQUE"
            ) {
              yields(ast.CreateRelationshipPropertyUniquenessConstraint(
                varFor("r1"),
                relTypeName("R"),
                Seq(prop("r2", "prop1"), prop("r3", "prop2")),
                Some("my_constraint"),
                ast.IfExistsThrowError,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT my_constraint $forOrOnString ()<-[r1:R]-() $requireOrAssertString (r2.prop) IS $relKeyWord UNIQUE"
            ) {
              yields(ast.CreateRelationshipPropertyUniquenessConstraint(
                varFor("r1"),
                relTypeName("R"),
                Seq(prop("r2", "prop")),
                Some("my_constraint"),
                ast.IfExistsThrowError,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT my_constraint $forOrOnString ()<-[r1:R]->() $requireOrAssertString (r2.prop) IS $relKeyWord UNIQUE"
            ) {
              yields(ast.CreateRelationshipPropertyUniquenessConstraint(
                varFor("r1"),
                relTypeName("R"),
                Seq(prop("r2", "prop")),
                Some("my_constraint"),
                ast.IfExistsThrowError,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE OR REPLACE CONSTRAINT my_constraint $forOrOnString ()-[r1:R]-() $requireOrAssertString (r2.prop) IS $relKeyWord UNIQUE"
            ) {
              yields(ast.CreateRelationshipPropertyUniquenessConstraint(
                varFor("r1"),
                relTypeName("R"),
                Seq(prop("r2", "prop")),
                Some("my_constraint"),
                ast.IfExistsReplace,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE OR REPLACE CONSTRAINT my_constraint IF NOT EXISTS $forOrOnString ()-[r1:R]-() $requireOrAssertString (r2.prop) IS $relKeyWord UNIQUE"
            ) {
              yields(ast.CreateRelationshipPropertyUniquenessConstraint(
                varFor("r1"),
                relTypeName("R"),
                Seq(prop("r2", "prop")),
                Some("my_constraint"),
                ast.IfExistsInvalidSyntax,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT my_constraint IF NOT EXISTS $forOrOnString ()-[r1:R]-() $requireOrAssertString (r2.prop) IS $relKeyWord UNIQUE"
            ) {
              yields(ast.CreateRelationshipPropertyUniquenessConstraint(
                varFor("r1"),
                relTypeName("R"),
                Seq(prop("r2", "prop")),
                Some("my_constraint"),
                ast.IfExistsDoNothing,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT my_constraint $forOrOnString ()-[r1:R]-() $requireOrAssertString (r2.prop) IS $relKeyWord UNIQUE OPTIONS {key: 'value'}"
            ) {
              yields(ast.CreateRelationshipPropertyUniquenessConstraint(
                varFor("r1"),
                relTypeName("R"),
                Seq(prop("r2", "prop")),
                Some("my_constraint"),
                ast.IfExistsThrowError,
                ast.OptionsMap(Map("key" -> literalString("value"))),
                containsOn,
                constraintVersion
              ))
            }
          })

          // Node property existence

          test(
            s"CREATE CONSTRAINT my_constraint $forOrOnString (node:Label) $requireOrAssertString (node.prop) IS NOT NULL"
          ) {
            yields(ast.CreateNodePropertyExistenceConstraint(
              varFor("node"),
              labelName("Label"),
              prop("node", "prop"),
              Some("my_constraint"),
              ast.IfExistsThrowError,
              ast.NoOptions,
              containsOn,
              constraintVersionOneOrTwo
            ))
          }

          test(
            s"CREATE OR REPLACE CONSTRAINT my_constraint $forOrOnString (node:Label) $requireOrAssertString node.prop IS NOT NULL"
          ) {
            yields(ast.CreateNodePropertyExistenceConstraint(
              varFor("node"),
              labelName("Label"),
              prop("node", "prop"),
              Some("my_constraint"),
              ast.IfExistsReplace,
              ast.NoOptions,
              containsOn,
              constraintVersionOneOrTwo
            ))
          }

          test(
            s"CREATE OR REPLACE CONSTRAINT my_constraint IF NOT EXISTS $forOrOnString (node:Label) $requireOrAssertString node.prop IS NOT NULL"
          ) {
            yields(ast.CreateNodePropertyExistenceConstraint(
              varFor("node"),
              labelName("Label"),
              prop("node", "prop"),
              Some("my_constraint"),
              ast.IfExistsInvalidSyntax,
              ast.NoOptions,
              containsOn,
              constraintVersionOneOrTwo
            ))
          }

          test(
            s"CREATE CONSTRAINT my_constraint IF NOT EXISTS $forOrOnString (node:Label) $requireOrAssertString node.prop IS NOT NULL"
          ) {
            yields(ast.CreateNodePropertyExistenceConstraint(
              varFor("node"),
              labelName("Label"),
              prop("node", "prop"),
              Some("my_constraint"),
              ast.IfExistsDoNothing,
              ast.NoOptions,
              containsOn,
              constraintVersionOneOrTwo
            ))
          }

          test(
            s"CREATE CONSTRAINT my_constraint $forOrOnString (node:Label) $requireOrAssertString node.prop IS NOT NULL OPTIONS {}"
          ) {
            yields(ast.CreateNodePropertyExistenceConstraint(
              varFor("node"),
              labelName("Label"),
              prop("node", "prop"),
              Some("my_constraint"),
              ast.IfExistsThrowError,
              ast.OptionsMap(Map.empty),
              containsOn,
              constraintVersionOneOrTwo
            ))
          }

          test(
            s"CREATE OR REPLACE CONSTRAINT my_constraint $forOrOnString (node:Label) $requireOrAssertString (node.prop2, node.prop3) IS NOT NULL"
          ) {
            assertFailsWithException(
              testName,
              new Neo4jASTConstructionException(
                ASTExceptionFactory.onlySinglePropertyAllowed(ConstraintType.NODE_IS_NOT_NULL)
              )
            )
          }

          // Relationship property existence

          test(
            s"CREATE CONSTRAINT `$$my_constraint` $forOrOnString ()-[r:R]-() $requireOrAssertString r.prop IS NOT NULL"
          ) {
            yields(ast.CreateRelationshipPropertyExistenceConstraint(
              varFor("r"),
              relTypeName("R"),
              prop("r", "prop"),
              Some("$my_constraint"),
              ast.IfExistsThrowError,
              ast.NoOptions,
              containsOn,
              constraintVersionOneOrTwo
            ))
          }

          test(
            s"CREATE CONSTRAINT my_constraint $forOrOnString ()-[r:R]-() $requireOrAssertString (r.prop) IS NOT NULL"
          ) {
            yields(ast.CreateRelationshipPropertyExistenceConstraint(
              varFor("r"),
              relTypeName("R"),
              prop("r", "prop"),
              Some("my_constraint"),
              ast.IfExistsThrowError,
              ast.NoOptions,
              containsOn,
              constraintVersionOneOrTwo
            ))
          }

          test(
            s"CREATE OR REPLACE CONSTRAINT `$$my_constraint` $forOrOnString ()-[r:R]-() $requireOrAssertString r.prop IS NOT NULL"
          ) {
            yields(ast.CreateRelationshipPropertyExistenceConstraint(
              varFor("r"),
              relTypeName("R"),
              prop("r", "prop"),
              Some("$my_constraint"),
              ast.IfExistsReplace,
              ast.NoOptions,
              containsOn,
              constraintVersionOneOrTwo
            ))
          }

          test(
            s"CREATE OR REPLACE CONSTRAINT `$$my_constraint` IF NOT EXISTS $forOrOnString ()-[r:R]->() $requireOrAssertString (r.prop) IS NOT NULL"
          ) {
            yields(ast.CreateRelationshipPropertyExistenceConstraint(
              varFor("r"),
              relTypeName("R"),
              prop("r", "prop"),
              Some("$my_constraint"),
              ast.IfExistsInvalidSyntax,
              ast.NoOptions,
              containsOn,
              constraintVersionOneOrTwo
            ))
          }

          test(
            s"CREATE CONSTRAINT `$$my_constraint` IF NOT EXISTS $forOrOnString ()<-[r:R]-() $requireOrAssertString r.prop IS NOT NULL"
          ) {
            yields(ast.CreateRelationshipPropertyExistenceConstraint(
              varFor("r"),
              relTypeName("R"),
              prop("r", "prop"),
              Some("$my_constraint"),
              ast.IfExistsDoNothing,
              ast.NoOptions,
              containsOn,
              constraintVersionOneOrTwo
            ))
          }

          test(
            s"CREATE OR REPLACE CONSTRAINT my_constraint $forOrOnString ()-[r1:REL]-() $requireOrAssertString (r2.prop2, r3.prop3) IS NOT NULL"
          ) {
            assertFailsWithException(
              testName,
              new Neo4jASTConstructionException(
                ASTExceptionFactory.onlySinglePropertyAllowed(ConstraintType.REL_IS_NOT_NULL)
              )
            )
          }

          Seq("IS TYPED", "IS ::", "::").foreach(typeKeyword => {

            // Node property type

            test(
              s"CREATE CONSTRAINT my_constraint $forOrOnString (node:Label) $requireOrAssertString (node.prop) $typeKeyword STRING"
            ) {
              yields(ast.CreateNodePropertyTypeConstraint(
                varFor("node"),
                labelName("Label"),
                prop("node", "prop"),
                expressions.StringTypeName(true),
                Some("my_constraint"),
                ast.IfExistsThrowError,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE OR REPLACE CONSTRAINT my_constraint $forOrOnString (node:Label) $requireOrAssertString node.prop $typeKeyword STRING"
            ) {
              yields(ast.CreateNodePropertyTypeConstraint(
                varFor("node"),
                labelName("Label"),
                prop("node", "prop"),
                expressions.StringTypeName(true),
                Some("my_constraint"),
                ast.IfExistsReplace,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE OR REPLACE CONSTRAINT my_constraint IF NOT EXISTS $forOrOnString (node:Label) $requireOrAssertString node.prop $typeKeyword STRING"
            ) {
              yields(ast.CreateNodePropertyTypeConstraint(
                varFor("node"),
                labelName("Label"),
                prop("node", "prop"),
                expressions.StringTypeName(true),
                Some("my_constraint"),
                ast.IfExistsInvalidSyntax,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT my_constraint IF NOT EXISTS $forOrOnString (node:Label) $requireOrAssertString node.prop $typeKeyword STRING"
            ) {
              yields(ast.CreateNodePropertyTypeConstraint(
                varFor("node"),
                labelName("Label"),
                prop("node", "prop"),
                expressions.StringTypeName(true),
                Some("my_constraint"),
                ast.IfExistsDoNothing,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT my_constraint $forOrOnString (node:Label) $requireOrAssertString node.prop $typeKeyword STRING OPTIONS {}"
            ) {
              yields(ast.CreateNodePropertyTypeConstraint(
                varFor("node"),
                labelName("Label"),
                prop("node", "prop"),
                expressions.StringTypeName(true),
                Some("my_constraint"),
                ast.IfExistsThrowError,
                ast.OptionsMap(Map.empty),
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE OR REPLACE CONSTRAINT my_constraint $forOrOnString (node:Label) $requireOrAssertString (node.prop2, node.prop3) $typeKeyword STRING"
            ) {
              assertFailsWithException(
                testName,
                new Neo4jASTConstructionException(
                  ASTExceptionFactory.onlySinglePropertyAllowed(ConstraintType.NODE_IS_TYPED)
                )
              )
            }

            // Relationship property type

            test(
              s"CREATE CONSTRAINT `$$my_constraint` $forOrOnString ()-[r:R]-() $requireOrAssertString r.prop $typeKeyword STRING"
            ) {
              yields(ast.CreateRelationshipPropertyTypeConstraint(
                varFor("r"),
                relTypeName("R"),
                prop("r", "prop"),
                expressions.StringTypeName(true),
                Some("$my_constraint"),
                ast.IfExistsThrowError,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT my_constraint $forOrOnString ()-[r:R]-() $requireOrAssertString (r.prop) $typeKeyword STRING"
            ) {
              yields(ast.CreateRelationshipPropertyTypeConstraint(
                varFor("r"),
                relTypeName("R"),
                prop("r", "prop"),
                expressions.StringTypeName(true),
                Some("my_constraint"),
                ast.IfExistsThrowError,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE OR REPLACE CONSTRAINT `$$my_constraint` $forOrOnString ()-[r:R]-() $requireOrAssertString r.prop $typeKeyword STRING"
            ) {
              yields(ast.CreateRelationshipPropertyTypeConstraint(
                varFor("r"),
                relTypeName("R"),
                prop("r", "prop"),
                expressions.StringTypeName(true),
                Some("$my_constraint"),
                ast.IfExistsReplace,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE OR REPLACE CONSTRAINT `$$my_constraint` IF NOT EXISTS $forOrOnString ()-[r:R]->() $requireOrAssertString (r.prop) $typeKeyword STRING"
            ) {
              yields(ast.CreateRelationshipPropertyTypeConstraint(
                varFor("r"),
                relTypeName("R"),
                prop("r", "prop"),
                expressions.StringTypeName(true),
                Some("$my_constraint"),
                ast.IfExistsInvalidSyntax,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE CONSTRAINT `$$my_constraint` IF NOT EXISTS $forOrOnString ()<-[r:R]-() $requireOrAssertString r.prop $typeKeyword STRING"
            ) {
              yields(ast.CreateRelationshipPropertyTypeConstraint(
                varFor("r"),
                relTypeName("R"),
                prop("r", "prop"),
                expressions.StringTypeName(true),
                Some("$my_constraint"),
                ast.IfExistsDoNothing,
                ast.NoOptions,
                containsOn,
                constraintVersion
              ))
            }

            test(
              s"CREATE OR REPLACE CONSTRAINT my_constraint $forOrOnString ()-[r1:REL]-() $requireOrAssertString (r2.prop2, r3.prop3) $typeKeyword STRING"
            ) {
              assertFailsWithException(
                testName,
                new Neo4jASTConstructionException(
                  ASTExceptionFactory.onlySinglePropertyAllowed(ConstraintType.REL_IS_TYPED)
                )
              )
            }
          })

          // Negative tests

          test(
            s"CREATE CONSTRAINT $forOrOnString (node:Label) $requireOrAssertString (node.prop) IS NODE KEY {indexProvider : 'range-1.0'}"
          ) {
            failsToParse
          }

          test(
            s"CREATE CONSTRAINT $forOrOnString (node:Label) $requireOrAssertString (node.prop) IS NODE KEY OPTIONS"
          ) {
            failsToParse
          }

          test(s"CREATE CONSTRAINT $forOrOnString (node:Label) $requireOrAssertString node.prop.part IS UNIQUE") {
            failsToParse
          }

          test(s"CREATE CONSTRAINT $forOrOnString (node:Label) $requireOrAssertString (node.prop.part) IS UNIQUE") {
            failsToParse
          }

          test(
            s"CREATE CONSTRAINT $forOrOnString (node:Label) $requireOrAssertString (node.prop) IS UNIQUE {indexProvider : 'range-1.0'}"
          ) {
            failsToParse
          }

          test(
            s"CREATE CONSTRAINT $forOrOnString (node:Label) $requireOrAssertString (node.prop1, node.prop2) IS UNIQUE OPTIONS"
          ) {
            failsToParse
          }

          test(
            s"CREATE CONSTRAINT $forOrOnString (node:Label) $requireOrAssertString (node.prop1, node.prop2) IS NOT NULL"
          ) {
            assertFailsWithMessage(testName, "Constraint type 'IS NOT NULL' does not allow multiple properties")
          }

          test(
            s"CREATE CONSTRAINT $forOrOnString ()-[r:R]-() $requireOrAssertString (r.prop1, r.prop2) IS NOT NULL"
          ) {
            assertFailsWithMessage(testName, "Constraint type 'IS NOT NULL' does not allow multiple properties")
          }

          test(s"CREATE CONSTRAINT $forOrOnString ()-[r1:REL]-() $requireOrAssertString (r2.prop) IS NODE KEY") {
            assertFailsWithMessageStart(
              testName,
              ASTExceptionFactory.relationshipPatternNotAllowed(ConstraintType.NODE_KEY)
            )
          }

          test(s"CREATE CONSTRAINT $forOrOnString ()-[r1:REL]-() $requireOrAssertString (r2.prop) IS NODE UNIQUE") {
            assertFailsWithMessageStart(
              testName,
              ASTExceptionFactory.relationshipPatternNotAllowed(ConstraintType.NODE_UNIQUE)
            )
          }

          test(s"CREATE CONSTRAINT $forOrOnString (node:Label) $requireOrAssertString (r.prop) IS RELATIONSHIP KEY") {
            assertFailsWithMessageStart(
              testName,
              ASTExceptionFactory.nodePatternNotAllowed(ConstraintType.REL_KEY)
            )
          }

          test(s"CREATE CONSTRAINT $forOrOnString (node:Label) $requireOrAssertString (r.prop) IS REL KEY") {
            assertFailsWithMessageStart(
              testName,
              ASTExceptionFactory.nodePatternNotAllowed(ConstraintType.REL_KEY)
            )
          }

          test(
            s"CREATE CONSTRAINT $forOrOnString (node:Label) $requireOrAssertString (r.prop) IS RELATIONSHIP UNIQUE"
          ) {
            assertFailsWithMessageStart(
              testName,
              ASTExceptionFactory.nodePatternNotAllowed(ConstraintType.REL_UNIQUE)
            )
          }

          test(s"CREATE CONSTRAINT $forOrOnString (node:Label) $requireOrAssertString (r.prop) IS REL UNIQUE") {
            assertFailsWithMessageStart(
              testName,
              ASTExceptionFactory.nodePatternNotAllowed(ConstraintType.REL_UNIQUE)
            )
          }

          test(
            s"CREATE CONSTRAINT $forOrOnString (node:Label) $requireOrAssertString node.prop IS UNIQUENESS"
          ) {
            failsToParse
          }

          test(
            s"CREATE CONSTRAINT $forOrOnString (node:Label) $requireOrAssertString node.prop IS NODE UNIQUENESS"
          ) {
            failsToParse
          }

          test(
            s"CREATE CONSTRAINT $forOrOnString ()-[r:R]-() $requireOrAssertString r.prop IS UNIQUENESS"
          ) {
            failsToParse
          }

          test(
            s"CREATE CONSTRAINT $forOrOnString ()-[r:R]-() $requireOrAssertString r.prop IS RELATIONSHIP UNIQUENESS"
          ) {
            failsToParse
          }

          test(
            s"CREATE CONSTRAINT $forOrOnString ()-[r:R]-() $requireOrAssertString r.prop IS REL UNIQUENESS"
          ) {
            failsToParse
          }
        }
    }

  // Property types

  // allowed types
  private val allowedNonListTypes = Seq(
    ("BOOL", expressions.BooleanTypeName(true)),
    ("BOOLEAN", expressions.BooleanTypeName(true)),
    ("VARCHAR", expressions.StringTypeName(true)),
    ("STRING", expressions.StringTypeName(true)),
    ("INTEGER", expressions.IntegerTypeName(true)),
    ("INT", expressions.IntegerTypeName(true)),
    ("SIGNED INTEGER", expressions.IntegerTypeName(true)),
    ("FLOAT", expressions.FloatTypeName(true)),
    ("DATE", expressions.DateTypeName(true)),
    ("LOCAL TIME", expressions.LocalTimeTypeName(true)),
    ("TIME WITHOUT TIMEZONE", expressions.LocalTimeTypeName(true)),
    ("ZONED TIME", expressions.ZonedTimeTypeName(true)),
    ("TIME WITH TIMEZONE", expressions.ZonedTimeTypeName(true)),
    ("LOCAL DATETIME", expressions.LocalDateTimeTypeName(true)),
    ("TIMESTAMP WITHOUT TIMEZONE", expressions.LocalDateTimeTypeName(true)),
    ("ZONED DATETIME", expressions.ZonedDateTimeTypeName(true)),
    ("TIMESTAMP WITH TIMEZONE", expressions.ZonedDateTimeTypeName(true)),
    ("DURATION", expressions.DurationTypeName(true)),
    ("POINT", expressions.PointTypeName(true))
  )

  allowedNonListTypes.foreach { case (typeString, typeExpr: expressions.CypherTypeName) =>
    test(s"CREATE CONSTRAINT FOR (n:Label) REQUIRE r.prop IS TYPED $typeString") {
      yields(ast.CreateNodePropertyTypeConstraint(
        varFor("n"),
        labelName("Label"),
        prop("r", "prop"),
        typeExpr,
        None,
        ast.IfExistsThrowError,
        ast.NoOptions,
        containsOn = false,
        ast.ConstraintVersion2
      ))
    }

    test(
      s"CREATE CONSTRAINT my_constraint FOR (n:Label) REQUIRE r.prop IS TYPED ${typeString.toLowerCase}"
    ) {
      yields(ast.CreateNodePropertyTypeConstraint(
        varFor("n"),
        labelName("Label"),
        prop("r", "prop"),
        typeExpr,
        Some("my_constraint"),
        ast.IfExistsThrowError,
        ast.NoOptions,
        containsOn = false,
        ast.ConstraintVersion2
      ))
    }

    test(s"CREATE CONSTRAINT FOR ()-[r:R]-() REQUIRE n.prop IS TYPED ${typeString.toLowerCase}") {
      yields(ast.CreateRelationshipPropertyTypeConstraint(
        varFor("r"),
        relTypeName("R"),
        prop("n", "prop"),
        typeExpr,
        None,
        ast.IfExistsThrowError,
        ast.NoOptions,
        containsOn = false,
        ast.ConstraintVersion2
      ))
    }

    test(
      s"CREATE CONSTRAINT my_constraint FOR ()-[r:R]-() REQUIRE n.prop IS TYPED $typeString"
    ) {
      yields(ast.CreateRelationshipPropertyTypeConstraint(
        varFor("r"),
        relTypeName("R"),
        prop("n", "prop"),
        typeExpr,
        Some("my_constraint"),
        ast.IfExistsThrowError,
        ast.NoOptions,
        containsOn = false,
        ast.ConstraintVersion2
      ))
    }
  }

  // disallowed types (throws in semantic checking)
  private val disallowedNonListTypes = Seq(
    ("NOTHING", NothingTypeName()),
    ("NOTHING NOT NULL", NothingTypeName()),
    ("NULL", NullTypeName()),
    ("NULL NOT NULL", NothingTypeName()),
    ("BOOL NOT NULL", BooleanTypeName(false)),
    ("BOOLEAN NOT NULL", BooleanTypeName(false)),
    ("VARCHAR NOT NULL", StringTypeName(false)),
    ("STRING NOT NULL", StringTypeName(false)),
    ("INTEGER NOT NULL", IntegerTypeName(false)),
    ("INT NOT NULL", IntegerTypeName(false)),
    ("SIGNED INTEGER NOT NULL", IntegerTypeName(false)),
    ("FLOAT NOT NULL", FloatTypeName(false)),
    ("DATE NOT NULL", DateTypeName(false)),
    ("LOCAL TIME NOT NULL", LocalTimeTypeName(false)),
    ("TIME WITHOUT TIMEZONE NOT NULL", LocalTimeTypeName(false)),
    ("ZONED TIME NOT NULL", ZonedTimeTypeName(false)),
    ("TIME WITH TIMEZONE NOT NULL", ZonedTimeTypeName(false)),
    ("LOCAL DATETIME NOT NULL", LocalDateTimeTypeName(false)),
    ("TIMESTAMP WITHOUT TIMEZONE NOT NULL", LocalDateTimeTypeName(false)),
    ("ZONED DATETIME NOT NULL", ZonedDateTimeTypeName(false)),
    ("TIMESTAMP WITH TIMEZONE NOT NULL", ZonedDateTimeTypeName(false)),
    ("DURATION NOT NULL", DurationTypeName(false)),
    ("POINT NOT NULL", PointTypeName(false)),
    ("NODE", NodeTypeName(true)),
    ("NODE NOT NULL", NodeTypeName(false)),
    ("ANY NODE", NodeTypeName(true)),
    ("ANY NODE NOT NULL", NodeTypeName(false)),
    ("VERTEX", NodeTypeName(true)),
    ("VERTEX NOT NULL", NodeTypeName(false)),
    ("ANY VERTEX", NodeTypeName(true)),
    ("ANY VERTEX NOT NULL", NodeTypeName(false)),
    ("RELATIONSHIP", RelationshipTypeName(true)),
    ("RELATIONSHIP NOT NULL", RelationshipTypeName(false)),
    ("ANY RELATIONSHIP", RelationshipTypeName(true)),
    ("ANY RELATIONSHIP NOT NULL", RelationshipTypeName(false)),
    ("EDGE", RelationshipTypeName(true)),
    ("EDGE NOT NULL", RelationshipTypeName(false)),
    ("ANY EDGE", RelationshipTypeName(true)),
    ("ANY EDGE NOT NULL", RelationshipTypeName(false)),
    ("MAP", MapTypeName(true)),
    ("MAP NOT NULL", MapTypeName(false)),
    ("ANY MAP", MapTypeName(true)),
    ("ANY MAP NOT NULL", MapTypeName(false)),
    ("PATH", PathTypeName(true)),
    ("PATH NOT NULL", PathTypeName(false)),
    ("ANY PROPERTY VALUE", PropertyValueTypeName(true)),
    ("ANY PROPERTY VALUE NOT NULL", PropertyValueTypeName(false)),
    ("PROPERTY VALUE", PropertyValueTypeName(true)),
    ("PROPERTY VALUE NOT NULL", PropertyValueTypeName(false)),
    ("ANY VALUE", AnyTypeName(true)),
    ("ANY VALUE NOT NULL", AnyTypeName(false)),
    ("ANY", AnyTypeName(true)),
    ("ANY NOT NULL", AnyTypeName(false))
  )

  disallowedNonListTypes.foreach { case (typeString, typeExpr: CypherTypeName) =>
    test(
      s"CREATE CONSTRAINT my_constraint FOR (n:Label) REQUIRE r.prop IS TYPED ${typeString.toLowerCase}"
    ) {
      yields(ast.CreateNodePropertyTypeConstraint(
        varFor("n"),
        labelName("Label"),
        prop("r", "prop"),
        typeExpr,
        Some("my_constraint"),
        ast.IfExistsThrowError,
        ast.NoOptions,
        containsOn = false,
        ast.ConstraintVersion2
      ))
    }

    test(
      s"CREATE CONSTRAINT my_constraint FOR ()-[r:R]-() REQUIRE n.prop IS TYPED $typeString"
    ) {
      yields(ast.CreateRelationshipPropertyTypeConstraint(
        varFor("r"),
        relTypeName("R"),
        prop("n", "prop"),
        typeExpr,
        Some("my_constraint"),
        ast.IfExistsThrowError,
        ast.NoOptions,
        containsOn = false,
        ast.ConstraintVersion2
      ))
    }
  }

  // List types (mix of allowed and disallowed types)
  (allowedNonListTypes ++ disallowedNonListTypes).foreach { case (innerTypeString, innerTypeExpr: CypherTypeName) =>
    Seq(
      // LIST<type>
      (s"LIST<$innerTypeString>", ListTypeName(innerTypeExpr, isNullable = true)),
      (s"LIST<$innerTypeString> NOT NULL", ListTypeName(innerTypeExpr, isNullable = false)),
      (s"ARRAY<$innerTypeString>", ListTypeName(innerTypeExpr, isNullable = true)),
      (s"ARRAY<$innerTypeString> NOT NULL", ListTypeName(innerTypeExpr, isNullable = false)),
      (s"$innerTypeString LIST", ListTypeName(innerTypeExpr, isNullable = true)),
      (s"$innerTypeString LIST NOT NULL", ListTypeName(innerTypeExpr, isNullable = false)),
      (s"$innerTypeString ARRAY", ListTypeName(innerTypeExpr, isNullable = true)),
      (s"$innerTypeString ARRAY NOT NULL", ListTypeName(innerTypeExpr, isNullable = false)),
      // LIST<LIST<type>>
      (
        s"LIST<LIST<$innerTypeString>>",
        ListTypeName(ListTypeName(innerTypeExpr, isNullable = true), isNullable = true)
      ),
      (
        s"LIST<LIST<$innerTypeString>> NOT NULL",
        ListTypeName(ListTypeName(innerTypeExpr, isNullable = true), isNullable = false)
      ),
      (
        s"LIST<LIST<$innerTypeString> NOT NULL>",
        ListTypeName(ListTypeName(innerTypeExpr, isNullable = false), isNullable = true)
      ),
      (
        s"LIST<LIST<$innerTypeString> NOT NULL> NOT NULL",
        ListTypeName(ListTypeName(innerTypeExpr, isNullable = false), isNullable = false)
      ),
      (
        s"LIST<ARRAY<$innerTypeString>>",
        ListTypeName(ListTypeName(innerTypeExpr, isNullable = true), isNullable = true)
      ),
      (
        s"LIST<ARRAY<$innerTypeString>> NOT NULL",
        ListTypeName(ListTypeName(innerTypeExpr, isNullable = true), isNullable = false)
      ),
      (
        s"LIST<ARRAY<$innerTypeString> NOT NULL>",
        ListTypeName(ListTypeName(innerTypeExpr, isNullable = false), isNullable = true)
      ),
      (
        s"LIST<ARRAY<$innerTypeString> NOT NULL> NOT NULL",
        ListTypeName(ListTypeName(innerTypeExpr, isNullable = false), isNullable = false)
      ),
      (s"LIST<$innerTypeString LIST>", ListTypeName(ListTypeName(innerTypeExpr, isNullable = true), isNullable = true)),
      (
        s"LIST<$innerTypeString LIST> NOT NULL",
        ListTypeName(ListTypeName(innerTypeExpr, isNullable = true), isNullable = false)
      ),
      (
        s"LIST<$innerTypeString LIST NOT NULL>",
        ListTypeName(ListTypeName(innerTypeExpr, isNullable = false), isNullable = true)
      ),
      (
        s"LIST<$innerTypeString LIST NOT NULL> NOT NULL",
        ListTypeName(ListTypeName(innerTypeExpr, isNullable = false), isNullable = false)
      ),
      (
        s"LIST<$innerTypeString ARRAY>",
        ListTypeName(ListTypeName(innerTypeExpr, isNullable = true), isNullable = true)
      ),
      (
        s"LIST<$innerTypeString ARRAY> NOT NULL",
        ListTypeName(ListTypeName(innerTypeExpr, isNullable = true), isNullable = false)
      ),
      (
        s"LIST<$innerTypeString ARRAY NOT NULL>",
        ListTypeName(ListTypeName(innerTypeExpr, isNullable = false), isNullable = true)
      ),
      (
        s"LIST<$innerTypeString ARRAY NOT NULL> NOT NULL",
        ListTypeName(ListTypeName(innerTypeExpr, isNullable = false), isNullable = false)
      ),
      (s"LIST<$innerTypeString> LIST", ListTypeName(ListTypeName(innerTypeExpr, isNullable = true), isNullable = true)),
      (
        s"LIST<$innerTypeString> LIST NOT NULL",
        ListTypeName(ListTypeName(innerTypeExpr, isNullable = true), isNullable = false)
      ),
      (
        s"LIST<$innerTypeString> NOT NULL LIST",
        ListTypeName(ListTypeName(innerTypeExpr, isNullable = false), isNullable = true)
      ),
      (
        s"LIST<$innerTypeString> NOT NULL LIST NOT NULL",
        ListTypeName(ListTypeName(innerTypeExpr, isNullable = false), isNullable = false)
      ),
      (
        s"ARRAY<$innerTypeString> LIST",
        ListTypeName(ListTypeName(innerTypeExpr, isNullable = true), isNullable = true)
      ),
      (
        s"ARRAY<$innerTypeString> LIST NOT NULL",
        ListTypeName(ListTypeName(innerTypeExpr, isNullable = true), isNullable = false)
      ),
      (
        s"ARRAY<$innerTypeString> NOT NULL LIST",
        ListTypeName(ListTypeName(innerTypeExpr, isNullable = false), isNullable = true)
      ),
      (
        s"ARRAY<$innerTypeString> NOT NULL LIST NOT NULL",
        ListTypeName(ListTypeName(innerTypeExpr, isNullable = false), isNullable = false)
      ),
      (s"$innerTypeString LIST LIST", ListTypeName(ListTypeName(innerTypeExpr, isNullable = true), isNullable = true)),
      (
        s"$innerTypeString LIST LIST NOT NULL",
        ListTypeName(ListTypeName(innerTypeExpr, isNullable = true), isNullable = false)
      ),
      (
        s"$innerTypeString LIST NOT NULL LIST",
        ListTypeName(ListTypeName(innerTypeExpr, isNullable = false), isNullable = true)
      ),
      (
        s"$innerTypeString LIST NOT NULL LIST NOT NULL",
        ListTypeName(ListTypeName(innerTypeExpr, isNullable = false), isNullable = false)
      ),
      (s"$innerTypeString ARRAY LIST", ListTypeName(ListTypeName(innerTypeExpr, isNullable = true), isNullable = true)),
      (
        s"$innerTypeString ARRAY LIST NOT NULL",
        ListTypeName(ListTypeName(innerTypeExpr, isNullable = true), isNullable = false)
      ),
      (
        s"$innerTypeString ARRAY NOT NULL LIST",
        ListTypeName(ListTypeName(innerTypeExpr, isNullable = false), isNullable = true)
      ),
      (
        s"$innerTypeString ARRAY NOT NULL LIST NOT NULL",
        ListTypeName(ListTypeName(innerTypeExpr, isNullable = false), isNullable = false)
      ),
      // even more nesting lists
      (
        s"LIST<LIST<LIST<LIST<$innerTypeString>> NOT NULL> NOT NULL LIST NOT NULL>",
        ListTypeName(
          ListTypeName(
            ListTypeName(
              ListTypeName(
                ListTypeName(
                  innerTypeExpr,
                  isNullable = true
                ),
                isNullable = false
              ),
              isNullable = false
            ),
            isNullable = false
          ),
          isNullable = true
        )
      ),
      (
        s"$innerTypeString LIST NOT NULL LIST LIST NOT NULL LIST",
        ListTypeName(
          ListTypeName(
            ListTypeName(
              ListTypeName(
                innerTypeExpr,
                isNullable = false
              ),
              isNullable = true
            ),
            isNullable = false
          ),
          isNullable = true
        )
      )
    ).foreach { case (listTypeString, listTypeExpr: CypherTypeName) =>
      test(
        s"CREATE CONSTRAINT my_constraint FOR (n:Label) REQUIRE r.prop IS TYPED ${listTypeString.toLowerCase}"
      ) {
        yields(ast.CreateNodePropertyTypeConstraint(
          varFor("n"),
          labelName("Label"),
          prop("r", "prop"),
          listTypeExpr,
          Some("my_constraint"),
          ast.IfExistsThrowError,
          ast.NoOptions,
          containsOn = false,
          ast.ConstraintVersion2
        ))
      }

      test(
        s"CREATE CONSTRAINT my_constraint FOR ()-[r:R]-() REQUIRE n.prop IS TYPED $listTypeString"
      ) {
        yields(ast.CreateRelationshipPropertyTypeConstraint(
          varFor("r"),
          relTypeName("R"),
          prop("n", "prop"),
          listTypeExpr,
          Some("my_constraint"),
          ast.IfExistsThrowError,
          ast.NoOptions,
          containsOn = false,
          ast.ConstraintVersion2
        ))
      }
    }
  }

  test("CREATE CONSTRAINT my_constraint FOR (n:L) REQUIRE n.p IS :: BOOLEAN LIST NOT NULL NOT NULL") {
    assertFailsWithMessage(
      testName,
      "Invalid input 'NOT': expected \"ARRAY\", \"LIST\", \"OPTIONS\" or <EOF> (line 1, column 83 (offset: 82))"
    )
  }

  // ASSERT EXISTS

  test("CREATE CONSTRAINT ON (node:Label) ASSERT EXISTS (node.prop)") {
    yields(ast.CreateNodePropertyExistenceConstraint(
      varFor("node"),
      labelName("Label"),
      prop("node", "prop"),
      None,
      ast.IfExistsThrowError,
      ast.NoOptions,
      containsOn = true,
      ast.ConstraintVersion0
    ))
  }

  test("CREATE CONSTRAINT ON (node1:Label) ASSERT EXISTS node2.prop") {
    assertAst(ast.CreateNodePropertyExistenceConstraint(
      varFor("node1", (1, 23, 22)),
      labelName("Label", (1, 29, 28)),
      Property(varFor("node2", (1, 50, 49)), PropertyKeyName("prop")((1, 56, 55)))((1, 50, 49)),
      None,
      ast.IfExistsThrowError,
      ast.NoOptions,
      containsOn = true,
      ast.ConstraintVersion0,
      None
    )(defaultPos))
  }

  test("CREATE OR REPLACE CONSTRAINT ON (node:Label) ASSERT EXISTS (node.prop)") {
    yields(ast.CreateNodePropertyExistenceConstraint(
      varFor("node"),
      labelName("Label"),
      prop("node", "prop"),
      None,
      ast.IfExistsReplace,
      ast.NoOptions,
      containsOn = true,
      ast.ConstraintVersion0
    ))
  }

  test("CREATE OR REPLACE CONSTRAINT IF NOT EXISTS ON (node:Label) ASSERT EXISTS (node.prop)") {
    yields(ast.CreateNodePropertyExistenceConstraint(
      varFor("node"),
      labelName("Label"),
      prop("node", "prop"),
      None,
      ast.IfExistsInvalidSyntax,
      ast.NoOptions,
      containsOn = true,
      ast.ConstraintVersion0
    ))
  }

  test("CREATE CONSTRAINT IF NOT EXISTS ON (node:Label) ASSERT EXISTS (node.prop)") {
    yields(ast.CreateNodePropertyExistenceConstraint(
      varFor("node"),
      labelName("Label"),
      prop("node", "prop"),
      None,
      ast.IfExistsDoNothing,
      ast.NoOptions,
      containsOn = true,
      ast.ConstraintVersion0
    ))
  }

  test("CREATE CONSTRAINT ON (node:Label) ASSERT EXISTS (node.prop) OPTIONS {}") {
    yields(ast.CreateNodePropertyExistenceConstraint(
      varFor("node"),
      labelName("Label"),
      prop("node", "prop"),
      None,
      ast.IfExistsThrowError,
      ast.OptionsMap(Map.empty),
      containsOn = true,
      ast.ConstraintVersion0
    ))
  }

  test("CREATE CONSTRAINT ON (node1:Label) ASSERT EXISTS (node2.prop1, node3.prop2)") {
    assertFailsWithException(
      testName,
      new Neo4jASTConstructionException(ASTExceptionFactory.onlySinglePropertyAllowed(ConstraintType.NODE_EXISTS))
    )
  }

  test("CREATE CONSTRAINT ON ()-[r:R]-() ASSERT EXISTS (r.prop)") {
    yields(ast.CreateRelationshipPropertyExistenceConstraint(
      varFor("r"),
      relTypeName("R"),
      prop("r", "prop"),
      None,
      ast.IfExistsThrowError,
      ast.NoOptions,
      containsOn = true,
      ast.ConstraintVersion0
    ))
  }

  test("CREATE CONSTRAINT ON ()-[r:R]->() ASSERT EXISTS (r.prop)") {
    yields(ast.CreateRelationshipPropertyExistenceConstraint(
      varFor("r"),
      relTypeName("R"),
      prop("r", "prop"),
      None,
      ast.IfExistsThrowError,
      ast.NoOptions,
      containsOn = true,
      ast.ConstraintVersion0
    ))
  }

  test("CREATE CONSTRAINT ON ()<-[r:R]-() ASSERT EXISTS (r.prop)") {
    yields(ast.CreateRelationshipPropertyExistenceConstraint(
      varFor("r"),
      relTypeName("R"),
      prop("r", "prop"),
      None,
      ast.IfExistsThrowError,
      ast.NoOptions,
      containsOn = true,
      ast.ConstraintVersion0
    ))
  }

  test("CREATE CONSTRAINT ON ()-[r1:R]-() ASSERT EXISTS r2.prop") {
    assertAst(ast.CreateRelationshipPropertyExistenceConstraint(
      varFor("r1", (1, 26, 25)),
      relTypeName("R", (1, 29, 28)),
      Property(varFor("r2", (1, 49, 48)), PropertyKeyName("prop")((1, 52, 51)))((1, 49, 48)),
      None,
      ast.IfExistsThrowError,
      ast.NoOptions,
      containsOn = true,
      ast.ConstraintVersion0,
      None
    )(defaultPos))
  }

  test("CREATE OR REPLACE CONSTRAINT ON ()<-[r:R]-() ASSERT EXISTS (r.prop)") {
    yields(ast.CreateRelationshipPropertyExistenceConstraint(
      varFor("r"),
      relTypeName("R"),
      prop("r", "prop"),
      None,
      ast.IfExistsReplace,
      ast.NoOptions,
      containsOn = true,
      ast.ConstraintVersion0
    ))
  }

  test("CREATE OR REPLACE CONSTRAINT IF NOT EXISTS ON ()-[r:R]-() ASSERT EXISTS (r.prop)") {
    yields(ast.CreateRelationshipPropertyExistenceConstraint(
      varFor("r"),
      relTypeName("R"),
      prop("r", "prop"),
      None,
      ast.IfExistsInvalidSyntax,
      ast.NoOptions,
      containsOn = true,
      ast.ConstraintVersion0
    ))
  }

  test("CREATE CONSTRAINT IF NOT EXISTS ON ()-[r:R]->() ASSERT EXISTS (r.prop)") {
    yields(ast.CreateRelationshipPropertyExistenceConstraint(
      varFor("r"),
      relTypeName("R"),
      prop("r", "prop"),
      None,
      ast.IfExistsDoNothing,
      ast.NoOptions,
      containsOn = true,
      ast.ConstraintVersion0
    ))
  }

  test("CREATE CONSTRAINT ON ()-[r1:REL]-() ASSERT EXISTS (r2.prop1, r3.prop2)") {
    assertFailsWithException(
      testName,
      new Neo4jASTConstructionException(ASTExceptionFactory.onlySinglePropertyAllowed(ConstraintType.REL_EXISTS))
    )
  }

  test("CREATE CONSTRAINT my_constraint ON (node:Label) ASSERT EXISTS (node.prop)") {
    yields(ast.CreateNodePropertyExistenceConstraint(
      varFor("node"),
      labelName("Label"),
      prop("node", "prop"),
      Some("my_constraint"),
      ast.IfExistsThrowError,
      ast.NoOptions,
      containsOn = true,
      ast.ConstraintVersion0
    ))
  }

  test("CREATE OR REPLACE CONSTRAINT my_constraint ON (node:Label) ASSERT EXISTS (node.prop)") {
    yields(ast.CreateNodePropertyExistenceConstraint(
      varFor("node"),
      labelName("Label"),
      prop("node", "prop"),
      Some("my_constraint"),
      ast.IfExistsReplace,
      ast.NoOptions,
      containsOn = true,
      ast.ConstraintVersion0
    ))
  }

  test("CREATE OR REPLACE CONSTRAINT my_constraint IF NOT EXISTS ON (node:Label) ASSERT EXISTS (node.prop)") {
    yields(ast.CreateNodePropertyExistenceConstraint(
      varFor("node"),
      labelName("Label"),
      prop("node", "prop"),
      Some("my_constraint"),
      ast.IfExistsInvalidSyntax,
      ast.NoOptions,
      containsOn = true,
      ast.ConstraintVersion0
    ))
  }

  test("CREATE CONSTRAINT my_constraint IF NOT EXISTS ON (node:Label) ASSERT EXISTS (node.prop)") {
    yields(ast.CreateNodePropertyExistenceConstraint(
      varFor("node"),
      labelName("Label"),
      prop("node", "prop"),
      Some("my_constraint"),
      ast.IfExistsDoNothing,
      ast.NoOptions,
      containsOn = true,
      ast.ConstraintVersion0
    ))
  }

  test("CREATE CONSTRAINT `$my_constraint` ON ()-[r:R]-() ASSERT EXISTS (r.prop)") {
    yields(ast.CreateRelationshipPropertyExistenceConstraint(
      varFor("r"),
      relTypeName("R"),
      prop("r", "prop"),
      Some("$my_constraint"),
      ast.IfExistsThrowError,
      ast.NoOptions,
      containsOn = true,
      ast.ConstraintVersion0
    ))
  }

  test("CREATE CONSTRAINT my_constraint ON ()-[r:R]-() ASSERT EXISTS (r.prop) OPTIONS {}") {
    yields(ast.CreateRelationshipPropertyExistenceConstraint(
      varFor("r"),
      relTypeName("R"),
      prop("r", "prop"),
      Some("my_constraint"),
      ast.IfExistsThrowError,
      ast.OptionsMap(Map.empty),
      containsOn = true,
      ast.ConstraintVersion0
    ))
  }

  test("CREATE OR REPLACE CONSTRAINT `$my_constraint` ON ()-[r:R]-() ASSERT EXISTS (r.prop)") {
    yields(ast.CreateRelationshipPropertyExistenceConstraint(
      varFor("r"),
      relTypeName("R"),
      prop("r", "prop"),
      Some("$my_constraint"),
      ast.IfExistsReplace,
      ast.NoOptions,
      containsOn = true,
      ast.ConstraintVersion0
    ))
  }

  test("CREATE OR REPLACE CONSTRAINT `$my_constraint` IF NOT EXISTS ON ()-[r:R]->() ASSERT EXISTS (r.prop)") {
    yields(ast.CreateRelationshipPropertyExistenceConstraint(
      varFor("r"),
      relTypeName("R"),
      prop("r", "prop"),
      Some("$my_constraint"),
      ast.IfExistsInvalidSyntax,
      ast.NoOptions,
      containsOn = true,
      ast.ConstraintVersion0
    ))
  }

  test("CREATE CONSTRAINT `$my_constraint` IF NOT EXISTS ON ()<-[r:R]-() ASSERT EXISTS (r.prop)") {
    yields(ast.CreateRelationshipPropertyExistenceConstraint(
      varFor("r"),
      relTypeName("R"),
      prop("r", "prop"),
      Some("$my_constraint"),
      ast.IfExistsDoNothing,
      ast.NoOptions,
      containsOn = true,
      ast.ConstraintVersion0
    ))
  }

  test(
    s"CREATE CONSTRAINT ON (node:Label) ASSERT EXISTS (node.prop1, node.prop2)"
  ) {
    assertFailsWithMessage(testName, "Constraint type 'EXISTS' does not allow multiple properties")
  }

  test(
    s"CREATE CONSTRAINT ON ()-[r:R]-() ASSERT EXISTS (r.prop1, r.prop2)"
  ) {
    assertFailsWithMessage(testName, "Constraint type 'EXISTS' does not allow multiple properties")
  }

  // Edge case tests

  test(
    "CREATE CONSTRAINT my_constraint FOR (n:Person) REQUIRE n.prop IS NOT NULL OPTIONS {indexProvider : 'range-1.0'};"
  ) {
    implicit val javaccRule: JavaccRule[Statement] = JavaccRule.Statements
    implicit val antlrRule: AntlrRule[Cst.Statement] = AntlrRule.Statements(checkAllTokensConsumed = false)

    yields(ast.CreateNodePropertyExistenceConstraint(
      varFor("n"),
      labelName("Person"),
      prop("n", "prop"),
      Some("my_constraint"),
      ast.IfExistsThrowError,
      ast.OptionsMap(Map("indexProvider" -> literalString("range-1.0"))),
      containsOn = false,
      constraintVersion = ast.ConstraintVersion2
    ))(javaccRule, antlrRule)
  }

  test(
    "CREATE CONSTRAINT FOR (n:Person) REQUIRE n.prop IS NOT NULL; CREATE CONSTRAINT FOR (n:User) REQUIRE n.prop IS UNIQUE"
  ) {
    implicit val javaccRule: JavaccRule[Statement] = JavaccRule.Statements
    implicit val antlrRule: AntlrRule[Cst.Statement] = AntlrRule.Statements(checkAllTokensConsumed = false)

    // The test setup does 'fromParser(_.Statements().get(0)', so only the first statement is yielded.
    // The purpose of the test is to make sure the parser does not throw an error on the semicolon, which was an issue before.
    // If we want to test that both statements are parsed, the test framework needs to be extended.
    yields(ast.CreateNodePropertyExistenceConstraint(
      varFor("n"),
      labelName("Person"),
      prop("n", "prop"),
      None,
      ast.IfExistsThrowError,
      ast.NoOptions,
      containsOn = false,
      constraintVersion = ast.ConstraintVersion2
    ))(javaccRule, antlrRule)
  }

  test("CREATE CONSTRAINT FOR FOR (node:Label) REQUIRE (node.prop) IS NODE KEY") {
    assertAst(ast.CreateNodeKeyConstraint(
      varFor("node", (1, 28, 27)),
      labelName("Label", (1, 33, 32)),
      Seq(prop("node", "prop", (1, 49, 48))),
      Some("FOR"),
      ast.IfExistsThrowError,
      ast.NoOptions,
      containsOn = false,
      ast.ConstraintVersion2
    )(defaultPos))
  }

  test("CREATE CONSTRAINT FOR FOR (node:Label) REQUIRE (node.prop) IS UNIQUE") {
    assertAst(ast.CreateNodePropertyUniquenessConstraint(
      varFor("node", (1, 28, 27)),
      labelName("Label", (1, 33, 32)),
      Seq(prop("node", "prop", (1, 49, 48))),
      Some("FOR"),
      ast.IfExistsThrowError,
      ast.NoOptions,
      containsOn = false,
      ast.ConstraintVersion2
    )(defaultPos))
  }

  test("CREATE CONSTRAINT FOR FOR (node:Label) REQUIRE node.prop IS NOT NULL") {
    assertAst(ast.CreateNodePropertyExistenceConstraint(
      varFor("node", (1, 28, 27)),
      labelName("Label", (1, 33, 32)),
      prop("node", "prop", (1, 48, 47)),
      Some("FOR"),
      ast.IfExistsThrowError,
      ast.NoOptions,
      containsOn = false,
      ast.ConstraintVersion2
    )(defaultPos))
  }

  test("CREATE CONSTRAINT FOR FOR ()-[r:R]-() REQUIRE (r.prop) IS NOT NULL") {
    assertAst(ast.CreateRelationshipPropertyExistenceConstraint(
      varFor("r", (1, 31, 30)),
      relTypeName("R", (1, 33, 32)),
      prop("r", "prop", (1, 48, 47)),
      Some("FOR"),
      ast.IfExistsThrowError,
      ast.NoOptions,
      containsOn = false,
      ast.ConstraintVersion2
    )(defaultPos))
  }

  // Negative tests

  test("CREATE CONSTRAINT my_constraint ON (node:Label) ASSERT EXISTS (node.prop) IS NOT NULL") {
    failsToParse
  }

  test("CREATE CONSTRAINT my_constraint ON ()-[r:R]-() ASSERT EXISTS (r.prop) IS NOT NULL") {
    assertFailsWithMessageStart(
      testName,
      "Invalid input 'IS': expected \"OPTIONS\" or <EOF> (line 1, column 71 (offset: 70))"
    )
  }

  test("CREATE CONSTRAINT $my_constraint ON ()-[r:R]-() ASSERT EXISTS (r.prop)") {
    assertFailsWithMessage(
      testName,
      "Invalid input '$': expected \"FOR\", \"IF\", \"ON\" or an identifier (line 1, column 19 (offset: 18))"
    )
  }

  test("CREATE CONSTRAINT FOR (n:Label) REQUIRE (n.prop)") {
    assertFailsWithMessage(testName, "Invalid input '': expected \"::\" or \"IS\" (line 1, column 49 (offset: 48))")
  }

  test("CREATE CONSTRAINT FOR (node:Label) REQUIRE EXISTS (node.prop)") {
    assertFailsWithMessage(testName, "Invalid input '(': expected \".\" (line 1, column 51 (offset: 50))")
  }

  test("CREATE CONSTRAINT FOR ()-[r:R]-() REQUIRE EXISTS (r.prop)") {
    assertFailsWithMessage(testName, "Invalid input '(': expected \".\" (line 1, column 50 (offset: 49))")
  }

  test(s"CREATE CONSTRAINT my_constraint ON ()-[r:R]-() ASSERT r.prop IS NULL") {
    assertFailsWithMessage(
      testName,
      """Invalid input 'NULL': expected
        |  "::"
        |  "KEY"
        |  "NODE"
        |  "NOT"
        |  "REL"
        |  "RELATIONSHIP"
        |  "TYPED"
        |  "UNIQUE" (line 1, column 65 (offset: 64))""".stripMargin
    )
  }

  test(s"CREATE CONSTRAINT my_constraint FOR ()-[r:R]-() REQUIRE r.prop IS NULL") {
    assertFailsWithMessage(
      testName,
      """Invalid input 'NULL': expected
        |  "::"
        |  "KEY"
        |  "NODE"
        |  "NOT"
        |  "REL"
        |  "RELATIONSHIP"
        |  "TYPED"
        |  "UNIQUE" (line 1, column 67 (offset: 66))""".stripMargin
    )
  }

  test(s"CREATE CONSTRAINT my_constraint ON (node:Label) ASSERT node.prop IS NULL") {
    assertFailsWithMessage(
      testName,
      """Invalid input 'NULL': expected
        |  "::"
        |  "KEY"
        |  "NODE"
        |  "NOT"
        |  "REL"
        |  "RELATIONSHIP"
        |  "TYPED"
        |  "UNIQUE" (line 1, column 69 (offset: 68))""".stripMargin
    )
  }

  test(s"CREATE CONSTRAINT my_constraint FOR (node:Label) REQUIRE node.prop IS NULL") {
    assertFailsWithMessage(
      testName,
      """Invalid input 'NULL': expected
        |  "::"
        |  "KEY"
        |  "NODE"
        |  "NOT"
        |  "REL"
        |  "RELATIONSHIP"
        |  "TYPED"
        |  "UNIQUE" (line 1, column 71 (offset: 70))""".stripMargin
    )
  }

  test("CREATE CONSTRAINT FOR (n:L) REQUIRE n.p IS TYPED") {
    assertFailsWithMessageStart(
      testName,
      """Invalid input '': expected
        |  "ANY"
        |  "ARRAY"
        |  "BOOL"
        |  "BOOLEAN"
        |  "DATE"
        |  "DURATION"
        |  "EDGE"
        |  "FLOAT"
        |  "INT"
        |  "INTEGER"
        |  "LIST"
        |  "LOCAL"
        |  "MAP"
        |  "NODE"
        |  "NOTHING"
        |  "PATH"
        |  "POINT"
        |  "PROPERTY"
        |  "RELATIONSHIP"
        |  "SIGNED"
        |  "STRING"
        |  "TIME"
        |  "TIMESTAMP"
        |  "VARCHAR"
        |  "VERTEX"
        |  "ZONED"
        |  "null"""".stripMargin
    )
  }

  test("CREATE CONSTRAINT FOR (n:L) REQUIRE n.p IS ::") {
    assertFailsWithMessageStart(
      testName,
      """Invalid input '': expected
        |  "ANY"
        |  "ARRAY"
        |  "BOOL"
        |  "BOOLEAN"
        |  "DATE"
        |  "DURATION"
        |  "EDGE"
        |  "FLOAT"
        |  "INT"
        |  "INTEGER"
        |  "LIST"
        |  "LOCAL"
        |  "MAP"
        |  "NODE"
        |  "NOTHING"
        |  "PATH"
        |  "POINT"
        |  "PROPERTY"
        |  "RELATIONSHIP"
        |  "SIGNED"
        |  "STRING"
        |  "TIME"
        |  "TIMESTAMP"
        |  "VARCHAR"
        |  "VERTEX"
        |  "ZONED"
        |  "null"""".stripMargin
    )
  }

  test("CREATE CONSTRAINT FOR (n:L) REQUIRE n.p ::") {
    assertFailsWithMessageStart(
      testName,
      """Invalid input '': expected
        |  "ANY"
        |  "ARRAY"
        |  "BOOL"
        |  "BOOLEAN"
        |  "DATE"
        |  "DURATION"
        |  "EDGE"
        |  "FLOAT"
        |  "INT"
        |  "INTEGER"
        |  "LIST"
        |  "LOCAL"
        |  "MAP"
        |  "NODE"
        |  "NOTHING"
        |  "PATH"
        |  "POINT"
        |  "PROPERTY"
        |  "RELATIONSHIP"
        |  "SIGNED"
        |  "STRING"
        |  "TIME"
        |  "TIMESTAMP"
        |  "VARCHAR"
        |  "VERTEX"
        |  "ZONED"
        |  "null"""".stripMargin
    )
  }

  test("CREATE CONSTRAINT FOR (n:L) REQUIRE n.p :: TYPED") {
    assertFailsWithMessage(
      testName,
      """Invalid input 'TYPED': expected
        |  "ANY"
        |  "ARRAY"
        |  "BOOL"
        |  "BOOLEAN"
        |  "DATE"
        |  "DURATION"
        |  "EDGE"
        |  "FLOAT"
        |  "INT"
        |  "INTEGER"
        |  "LIST"
        |  "LOCAL"
        |  "MAP"
        |  "NODE"
        |  "NOTHING"
        |  "PATH"
        |  "POINT"
        |  "PROPERTY"
        |  "RELATIONSHIP"
        |  "SIGNED"
        |  "STRING"
        |  "TIME"
        |  "TIMESTAMP"
        |  "VARCHAR"
        |  "VERTEX"
        |  "ZONED"
        |  "null" (line 1, column 44 (offset: 43))""".stripMargin
    )
  }

  test("CREATE CONSTRAINT FOR (n:L) REQUIRE n.p :: UNIQUE") {
    assertFailsWithMessage(
      testName,
      """Invalid input 'UNIQUE': expected
        |  "ANY"
        |  "ARRAY"
        |  "BOOL"
        |  "BOOLEAN"
        |  "DATE"
        |  "DURATION"
        |  "EDGE"
        |  "FLOAT"
        |  "INT"
        |  "INTEGER"
        |  "LIST"
        |  "LOCAL"
        |  "MAP"
        |  "NODE"
        |  "NOTHING"
        |  "PATH"
        |  "POINT"
        |  "PROPERTY"
        |  "RELATIONSHIP"
        |  "SIGNED"
        |  "STRING"
        |  "TIME"
        |  "TIMESTAMP"
        |  "VARCHAR"
        |  "VERTEX"
        |  "ZONED"
        |  "null" (line 1, column 44 (offset: 43))""".stripMargin
    )
  }

  test("CREATE CONSTRAINT FOR (n:L) REQUIRE n.p :: BOOLEAN UNIQUE") {
    assertFailsWithMessageStart(
      testName,
      """Invalid input 'UNIQUE': expected
        |  "ARRAY"
        |  "LIST"
        |  "NOT"
        |  "OPTIONS"
        |  <EOF>""".stripMargin
    )
  }

  test("CREATE CONSTRAINT FOR (n:L) REQUIRE n.p IS :: BOOL EAN") {
    assertFailsWithMessageStart(
      testName,
      """Invalid input 'EAN': expected
        |  "ARRAY"
        |  "LIST"
        |  "NOT"
        |  "OPTIONS"
        |  <EOF>""".stripMargin
    )
  }

  // Drop constraint

  test("DROP CONSTRAINT ON (node:Label) ASSERT (node.prop) IS NODE KEY") {
    yields(ast.DropNodeKeyConstraint(varFor("node"), labelName("Label"), Seq(prop("node", "prop"))))
  }

  test("DROP CONSTRAINT ON (node1:Label) ASSERT node2.prop IS NODE KEY") {
    assertAst(
      ast.DropNodeKeyConstraint(
        varFor("node1", (1, 21, 20)),
        labelName("Label", (1, 27, 26)),
        Seq(Property(varFor("node2", (1, 41, 40)), PropertyKeyName("prop")((1, 47, 46)))((1, 42, 40))),
        None
      )(defaultPos)
    )
  }

  test("DROP CONSTRAINT ON (node:Label) ASSERT (node.prop1,node.prop2) IS NODE KEY") {
    yields(ast.DropNodeKeyConstraint(
      varFor("node"),
      labelName("Label"),
      Seq(prop("node", "prop1"), prop("node", "prop2"))
    ))
  }

  test("DROP CONSTRAINT ON ()-[r1:R]-() ASSERT r2.prop IS NODE KEY") {
    assertFailsWithMessageStart(testName, ASTExceptionFactory.relationshipPatternNotAllowed(ConstraintType.NODE_KEY))
  }

  test("DROP CONSTRAINT ON (node:Label) ASSERT node.prop IS UNIQUE") {
    yields(ast.DropPropertyUniquenessConstraint(varFor("node"), labelName("Label"), Seq(prop("node", "prop"))))
  }

  test("DROP CONSTRAINT ON (node:Label) ASSERT (node.prop) IS UNIQUE") {
    yields(ast.DropPropertyUniquenessConstraint(varFor("node"), labelName("Label"), Seq(prop("node", "prop"))))
  }

  test("DROP CONSTRAINT ON (node:Label) ASSERT (node.prop1,node.prop2) IS UNIQUE") {
    yields(ast.DropPropertyUniquenessConstraint(
      varFor("node"),
      labelName("Label"),
      Seq(prop("node", "prop1"), prop("node", "prop2"))
    ))
  }

  test("DROP CONSTRAINT ON ()-[r1:R]-() ASSERT r2.prop IS UNIQUE") {
    assertFailsWithMessageStart(testName, ASTExceptionFactory.relationshipPatternNotAllowed(ConstraintType.NODE_UNIQUE))
  }

  test("DROP CONSTRAINT ON (node:Label) ASSERT EXISTS (node.prop)") {
    yields(ast.DropNodePropertyExistenceConstraint(varFor("node"), labelName("Label"), prop("node", "prop")))
  }

  test("DROP CONSTRAINT ON (node1:Label) ASSERT EXISTS node2.prop") {
    assertAst(
      ast.DropNodePropertyExistenceConstraint(
        varFor("node1", (1, 21, 20)),
        labelName("Label", (1, 27, 26)),
        Property(varFor("node2", (1, 48, 47)), PropertyKeyName("prop")((1, 54, 53)))((1, 48, 47)),
        None
      )(defaultPos)
    )
  }

  test("DROP CONSTRAINT ON ()-[r:R]-() ASSERT EXISTS (r.prop)") {
    yields(ast.DropRelationshipPropertyExistenceConstraint(varFor("r"), relTypeName("R"), prop("r", "prop")))
  }

  test("DROP CONSTRAINT ON ()-[r:R]->() ASSERT EXISTS (r.prop)") {
    yields(ast.DropRelationshipPropertyExistenceConstraint(varFor("r"), relTypeName("R"), prop("r", "prop")))
  }

  test("DROP CONSTRAINT ON ()<-[r:R]-() ASSERT EXISTS (r.prop)") {
    yields(ast.DropRelationshipPropertyExistenceConstraint(varFor("r"), relTypeName("R"), prop("r", "prop")))
  }

  test("DROP CONSTRAINT ON ()-[r1:R]-() ASSERT EXISTS r2.prop") {
    assertAst(
      ast.DropRelationshipPropertyExistenceConstraint(
        varFor("r1", (1, 24, 23)),
        relTypeName("R", (1, 27, 26)),
        Property(varFor("r2", (1, 47, 46)), PropertyKeyName("prop")((1, 50, 49)))((1, 47, 46)),
        None
      )(defaultPos)
    )
  }

  test("DROP CONSTRAINT ON (node:Label) ASSERT (node.prop) IS NOT NULL") {
    assertFailsWithException(testName, new Neo4jASTConstructionException(ASTExceptionFactory.invalidDropCommand))
  }

  test("DROP CONSTRAINT ON (node:Label) ASSERT node.prop IS NOT NULL") {
    assertFailsWithException(testName, new Neo4jASTConstructionException(ASTExceptionFactory.invalidDropCommand))
  }

  test("DROP CONSTRAINT ON ()-[r:R]-() ASSERT (r.prop) IS NOT NULL") {
    assertFailsWithException(testName, new Neo4jASTConstructionException(ASTExceptionFactory.invalidDropCommand))
  }

  test("DROP CONSTRAINT ON ()-[r:R]-() ASSERT r.prop IS NOT NULL") {
    assertFailsWithException(testName, new Neo4jASTConstructionException(ASTExceptionFactory.invalidDropCommand))
  }

  test("DROP CONSTRAINT ON (node:Label) ASSERT (node.EXISTS) IS NODE KEY") {
    yields(ast.DropNodeKeyConstraint(varFor("node"), labelName("Label"), Seq(prop("node", "EXISTS"))))
  }

  test("DROP CONSTRAINT ON (node:Label) ASSERT (node.EXISTS) IS UNIQUE") {
    yields(ast.DropPropertyUniquenessConstraint(varFor("node"), labelName("Label"), Seq(prop("node", "EXISTS"))))
  }

  test("DROP CONSTRAINT ON (node:Label) ASSERT EXISTS (node.EXISTS)") {
    yields(ast.DropNodePropertyExistenceConstraint(varFor("node"), labelName("Label"), prop("node", "EXISTS")))
  }

  test("DROP CONSTRAINT ON ()-[r:R]-() ASSERT EXISTS (r.EXISTS)") {
    yields(ast.DropRelationshipPropertyExistenceConstraint(varFor("r"), relTypeName("R"), prop("r", "EXISTS")))
  }

  test("DROP CONSTRAINT my_constraint") {
    yields(ast.DropConstraintOnName("my_constraint", ifExists = false))
  }

  test("DROP CONSTRAINT `$my_constraint`") {
    yields(ast.DropConstraintOnName("$my_constraint", ifExists = false))
  }

  test("DROP CONSTRAINT my_constraint IF EXISTS") {
    yields(ast.DropConstraintOnName("my_constraint", ifExists = true))
  }

  test("DROP CONSTRAINT $my_constraint") {
    failsToParse
  }

  test("DROP CONSTRAINT my_constraint IF EXISTS;") {
    implicit val javaccRule: JavaccRule[Statement] = JavaccRule.Statements
    implicit val antlrRule: AntlrRule[Cst.Statement] = AntlrRule.Statements(checkAllTokensConsumed = false)

    yields(ast.DropConstraintOnName("my_constraint", ifExists = true))(javaccRule, antlrRule)
  }

  test("DROP CONSTRAINT my_constraint; DROP CONSTRAINT my_constraint2;") {
    implicit val javaccRule: JavaccRule[Statement] = JavaccRule.Statements
    implicit val antlrRule: AntlrRule[Cst.Statement] = AntlrRule.Statements(checkAllTokensConsumed = false)

    // The test setup does 'fromParser(_.Statements().get(0)', so only the first statement is yielded.
    // The purpose of the test is to make sure the parser does not throw an error on the semicolon, which was an issue before.
    // If we want to test that both statements are parsed, the test framework needs to be extended.
    yields(ast.DropConstraintOnName("my_constraint", ifExists = false))(javaccRule, antlrRule)
  }

  test("DROP CONSTRAINT my_constraint ON (node:Label) ASSERT (node.prop1,node.prop2) IS NODE KEY") {
    assertFailsWithMessage(testName, "Invalid input 'ON': expected \"IF\" or <EOF> (line 1, column 31 (offset: 30))")
  }
}
