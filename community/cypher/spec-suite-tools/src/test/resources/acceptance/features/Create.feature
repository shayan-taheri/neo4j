#
# Copyright (c) "Neo4j"
# Neo4j Sweden AB [http://neo4j.com]
#
# This file is part of Neo4j.
#
# Neo4j is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <http://www.gnu.org/licenses/>.
#

#encoding: utf-8

Feature: Create

  Scenario: Dependencies in creating multiple nodes
    Given an empty graph
    When executing query:
      """
      CREATE (a {prop: 1}), ({prop: a.prop})
      """
    Then the result should be empty
    And the side effects should be:
      | +nodes      | 2 |
      | +properties | 2 |

  Scenario: Dependencies in creating multiple rels
    Given an empty graph
    When executing query:
      """
      CREATE ()-[r:R {prop:1}]->(), ()-[q:R {prop:r.prop}]->()
      """
    Then the result should be empty
    And the side effects should be:
      | +nodes         | 4 |
      | +relationships | 2 |
      | +properties    | 2 |

  Scenario: Create with reverse of toStringOrNull
    Given an empty graph
    When executing query:
      """
      CREATE (n) RETURN reverse(toStringOrNull(n)) AS result
      """
    Then the result should be, in order:
      | result |
      | null   |
    And the side effects should be:
      | +nodes         | 1 |

  Scenario: Dependencies between nodes and relationships single clause
    Given an empty graph
    When executing query:
      """
      CREATE (:A)-[m:R {p:'hello'}]->(:B), (c:C {t:type(m), p:m.p}) RETURN m, c
      """
    Then the result should be, in order:
      | m                 | c                         |
      | [:R {p: 'hello'}] | (:C {t: 'R', p: 'hello'}) |
    And the side effects should be:
      | +nodes            | 3 |
      | +relationships    | 1 |
      | +properties       | 3 |
      | +labels           | 3 |

  Scenario: Dependencies between nodes and relationships separate clauses
    Given an empty graph
    When executing query:
      """
      CREATE (:A)-[m:R {p:'hello'}]->(:B) CREATE (c:C {t:type(m), p:m.p}) RETURN m, c
      """
    Then the result should be, in order:
      | m                 | c                         |
      | [:R {p: 'hello'}] | (:C {t: 'R', p: 'hello'}) |
    And the side effects should be:
      | +nodes            | 3 |
      | +relationships    | 1 |
      | +properties       | 3 |
      | +labels           | 3 |

  Scenario: Dependencies between nodes and relationships two clauses with count single clause
    Given an empty graph
    When executing query:
      """
      CREATE ({x:0})<-[y:A]-() CREATE ({x:endNode(y).x})
      RETURN COUNT { ({x:0}) } AS n
    """
    Then the result should be, in order:
      | n |
      | 2 |
    And the side effects should be:
      | +nodes            | 3 |
      | +relationships    | 1 |
      | +properties       | 2 |

  Scenario: Dependencies between nodes and relationships two clauses with count separate clauses
    Given an empty graph
    When executing query:
      """
      CREATE ({x:0})<-[y:A]-() CREATE ({x:endNode(y).x})
      RETURN COUNT { ({x:0}) } AS n
    """
    Then the result should be, in order:
      | n |
      | 2 |
    And the side effects should be:
      | +nodes            | 3 |
      | +relationships    | 1 |
      | +properties       | 2 |


  Scenario: Dependencies between nodes and relationships longer pattern
    Given an empty graph
    When executing query:
      """
      CREATE
      (n1 {np1: 'node1'}),
      (n1)-[r1:R {np1: n1.p1, rp1: 'rel1'}]->(n2 {np1: n1.np1, np2: 'node2'}),
      (n2)-[r2:R {np1: n1.np1, rp1: r1.rp1, np2: n2.np2, rp2: 'rel2'}]->(n3 {np1 : n1.np1, rp1: r1.rp1, np2: n2.np2, np3: 'node3'}) RETURN r2, n3
      """
    Then the result should be, in order:
      | r2                                                          | n3                                                        |
      | [:R {np1: 'node1', np2: 'node2', rp1: 'rel1', rp2: 'rel2'}] | ({np1: 'node1', np2: 'node2', rp1: 'rel1', np3: 'node3'}) |
    And the side effects should be:
      | +nodes            | 3  |
      | +relationships    | 2  |
      | +properties       | 12 |
