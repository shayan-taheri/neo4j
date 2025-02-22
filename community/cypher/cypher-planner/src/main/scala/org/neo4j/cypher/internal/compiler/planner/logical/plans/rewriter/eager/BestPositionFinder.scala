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
package org.neo4j.cypher.internal.compiler.planner.logical.plans.rewriter.eager

import org.neo4j.cypher.internal.compiler.planner.logical.plans.rewriter.eager.CandidateListFinder.CandidateList
import org.neo4j.cypher.internal.ir.EagernessReason
import org.neo4j.cypher.internal.logical.plans.LogicalPlan
import org.neo4j.cypher.internal.planner.spi.PlanningAttributes.Cardinalities
import org.neo4j.cypher.internal.util.Ref
import org.neo4j.cypher.internal.util.attribution.Id

import scala.collection.immutable.ListSet
import scala.collection.mutable

/**
 * Given the candidate lists, finds the best positions for Eager plans by looking at Cardinalities and trying to merge candidate lists,
 */
object BestPositionFinder {

  /**
   * If we have more candidateLists than this, then we won't attempt to merge them,
   * and insert Eager at the local optima of each candidateList.
   */
  private val SIZE_LIMIT = 50

  /**
   * @param candidates the candidate plans
   * @param minimum    the plan with the lowest cardinality in candidates
   * @param reasons    all reasons that contributed to this candidateSet
   */
  private case class CandidateSetWithMinimum(
    candidates: Set[Ref[LogicalPlan]],
    minimum: Ref[LogicalPlan],
    reasons: Set[EagernessReason]
  )

  /**
   * By looking at the candidates in all lists, pick the best locations for planning Eager.
   *
   * @return a map from a plan id on which we need to plan Eager to the EagernessReasons.
   */
  private[eager] def pickPlansToEagerize(
    cardinalities: Cardinalities,
    candidateLists: Seq[CandidateList]
  ): Map[Id, ListSet[EagernessReason]] = {
    // Find the minimum of each candidate set
    val csWithMinima = candidateLists.map(cl =>
      CandidateSetWithMinimum(
        cl.candidates.toSet,
        cl.candidates.minBy(plan => cardinalities.get(plan.value.id)),
        cl.conflict.reasons
      )
    )

    /**
     * Merge two candidate sets if they overlap.
     */
    def tryMerge(a: CandidateSetWithMinimum, b: CandidateSetWithMinimum): Option[CandidateSetWithMinimum] = {
      val aSet = a.candidates
      val bSet = b.candidates
      val intersection = aSet intersect bSet

      if (intersection.isEmpty) {
        // We cannot merge non-intersecting sets
        None
      } else if (aSet subsetOf bSet) {
        // If a is a subset of b, we can return that a, with merged reasons.
        Some(a.copy(reasons = a.reasons ++ b.reasons))
      } else if (bSet subsetOf aSet) {
        // If b is a subset of a, we can return that b, with merged reasons.
        Some(b.copy(reasons = a.reasons ++ b.reasons))
      } else if (a.minimum == b.minimum || intersection.contains(a.minimum)) {
        // If they have the same minimum, or a's minimum lies in the intersection,
        // return the intersection of both sets with a's minimum and merged reasons.
        Some(CandidateSetWithMinimum(intersection, a.minimum, a.reasons ++ b.reasons))
      } else if (intersection.contains(b.minimum)) {
        // If b's minimum lies in the intersection,
        // return the intersection of both sets with b's minimum and merged reasons.
        Some(CandidateSetWithMinimum(intersection, b.minimum, a.reasons ++ b.reasons))
      } else {
        // Both sets have their own minima, and neither lies in the intersection.
        None
      }
    }

    val results = if (csWithMinima.size > SIZE_LIMIT) {
      csWithMinima
    } else {
      // Try to find the best overall location by looking at all candidate sets.
      // If there are situations where sets more than pairwise overlap, this algorithm will not necessarily find the global optimum,
      // since it only tries pairwise merging of sequences. But, it "only" has quadratic complexity.
      val buffer = mutable.ArrayBuffer[CandidateSetWithMinimum]()
      csWithMinima.foreach { listA =>
        if (buffer.isEmpty) {
          buffer += listA
        } else {
          var merged = false
          val it = buffer.zipWithIndex.iterator

          // Go through all lists already in results and see if the current one can get merged with any other list.
          while (!merged && it.hasNext) {
            val (listB, i) = it.next()
            tryMerge(listA, listB) match {
              case Some(mergedList) =>
                // If so, only keep the merged list
                merged = true
                buffer.remove(i)
                buffer += mergedList
              case None =>
                // Otherwise keep both
                buffer += listA
            }
          }
        }
      }
      buffer
    }

    results
      .map(cl => cl.minimum -> cl.reasons)
      .groupBy(_._1.value.id)
      .view
      .mapValues(_.view.flatMap(_._2).to(ListSet))
      .toMap
  }

}
