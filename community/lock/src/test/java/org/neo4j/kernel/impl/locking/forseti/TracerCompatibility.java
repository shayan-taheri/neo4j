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
package org.neo4j.kernel.impl.locking.forseti;

import static java.lang.String.format;
import static org.neo4j.lock.ResourceType.NODE;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.neo4j.lock.LockTracer;
import org.neo4j.lock.LockType;
import org.neo4j.lock.LockWaitEvent;
import org.neo4j.lock.ResourceType;

abstract class TracerCompatibility extends LockCompatibilityTestSupport {
    TracerCompatibility(LockingCompatibilityTest suite) {
        super(suite);
    }

    @Test
    void shouldTraceWaitTimeWhenTryingToAcquireExclusiveLockAndExclusiveIsHeld() throws Exception {
        // given
        Tracer tracerA = new Tracer();
        Tracer tracerB = new Tracer();
        clientA.acquireExclusive(tracerA, NODE, 17);

        // when
        Future<Void> future = acquireExclusive(clientB, tracerB, NODE, 17).callAndAssertWaiting();

        // then
        clientA.releaseExclusive(NODE, 17);
        future.get();
        tracerA.assertCalls(0);
        tracerB.assertCalls(1);
    }

    @Test
    void shouldTraceWaitTimeWhenTryingToAcquireSharedLockAndExclusiveIsHeld() throws Exception {
        // given
        Tracer tracerA = new Tracer();
        Tracer tracerB = new Tracer();
        clientA.acquireExclusive(tracerA, NODE, 17);

        // when
        Future<Void> future = acquireShared(clientB, tracerB, NODE, 17).callAndAssertWaiting();

        // then
        clientA.releaseExclusive(NODE, 17);
        future.get();
        tracerA.assertCalls(0);
        tracerB.assertCalls(1);
    }

    @Test
    void shouldTraceWaitTimeWhenTryingToAcquireExclusiveLockAndSharedIsHeld() throws Exception {
        // given
        Tracer tracerA = new Tracer();
        Tracer tracerB = new Tracer();
        clientA.acquireShared(tracerA, NODE, 17);

        // when
        Future<Void> future = acquireExclusive(clientB, tracerB, NODE, 17).callAndAssertWaiting();

        // then
        clientA.releaseShared(NODE, 17);
        future.get();
        tracerA.assertCalls(0);
        tracerB.assertCalls(1);
    }

    static class Tracer implements LockTracer, LockWaitEvent {
        int done;
        final List<StackTraceElement[]> waitCalls = new ArrayList<>();

        @Override
        public LockWaitEvent waitForLock(
                LockType lockType, ResourceType resourceType, long transactionId, long... resourceIds) {
            waitCalls.add(Thread.currentThread().getStackTrace());
            return this;
        }

        @Override
        public void close() {
            done++;
        }

        void assertCalls(int expected) {
            if (waitCalls.size() != done) {
                throw withCallTraces(new AssertionError("Should complete waiting as many times as started."));
            }
            if (done != expected) {
                throw withCallTraces(new AssertionError(format("Expected %d calls, but got %d", expected, done)));
            }
        }

        private <EX extends Throwable> EX withCallTraces(EX failure) {
            for (StackTraceElement[] waitCall : waitCalls) {
                RuntimeException call = new RuntimeException("Wait called");
                call.setStackTrace(waitCall);
                failure.addSuppressed(call);
            }
            return failure;
        }
    }
}
