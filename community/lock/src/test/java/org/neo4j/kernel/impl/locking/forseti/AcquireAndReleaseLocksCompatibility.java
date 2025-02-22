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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.neo4j.lock.ResourceType.NODE;

import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.neo4j.kernel.impl.locking.LockCountVisitor;
import org.neo4j.lock.LockTracer;

/**
 * Tests simple acquiring and releasing of single locks.
 * For testing "stacking" locks on the same client, see {@link LockReentrancyCompatibility}.
 **/
abstract class AcquireAndReleaseLocksCompatibility extends LockCompatibilityTestSupport {
    AcquireAndReleaseLocksCompatibility(LockingCompatibilityTest suite) {
        super(suite);
    }

    @Test
    void exclusiveShouldWaitForExclusive() {
        // When
        clientA.acquireExclusive(LockTracer.NONE, NODE, 1L);

        // Then
        Future<Void> clientBLock =
                acquireExclusive(clientB, LockTracer.NONE, NODE, 1L).callAndAssertWaiting();

        // And when
        clientA.releaseExclusive(NODE, 1L);

        // Then this should not block
        assertNotWaiting(clientBLock);
    }

    @Test
    void exclusiveShouldWaitForShared() {
        // When
        clientA.acquireShared(LockTracer.NONE, NODE, 1L);

        // Then other shared locks are allowed
        clientC.acquireShared(LockTracer.NONE, NODE, 1L);

        // But exclusive locks should wait
        Future<Void> clientBLock =
                acquireExclusive(clientB, LockTracer.NONE, NODE, 1L).callAndAssertWaiting();

        // And when
        clientA.releaseShared(NODE, 1L);
        clientC.releaseShared(NODE, 1L);

        // Then this should not block
        assertNotWaiting(clientBLock);
    }

    @Test
    void sharedShouldWaitForExclusive() {
        // When
        clientA.acquireExclusive(LockTracer.NONE, NODE, 1L);

        // Then shared locks should wait
        Future<Void> clientBLock =
                acquireShared(clientB, LockTracer.NONE, NODE, 1L).callAndAssertWaiting();

        // And when
        clientA.releaseExclusive(NODE, 1L);

        // Then this should not block
        assertNotWaiting(clientBLock);
    }

    @Test
    void shouldTrySharedLock() {
        // Given I've grabbed a share lock
        assertTrue(clientA.trySharedLock(NODE, 1L));

        // Then other clients can't have exclusive locks
        assertFalse(clientB.tryExclusiveLock(NODE, 1L));

        // But they are allowed share locks
        assertTrue(clientB.trySharedLock(NODE, 1L));
    }

    @Test
    void shouldTryExclusiveLock() {
        // Given I've grabbed an exclusive lock
        assertTrue(clientA.tryExclusiveLock(NODE, 1L));

        // Then other clients can't have exclusive locks
        assertFalse(clientB.tryExclusiveLock(NODE, 1L));

        // Nor can they have share locks
        assertFalse(clientB.trySharedLock(NODE, 1L));
    }

    @Test
    void shouldTryUpgradeSharedToExclusive() {
        // Given I've grabbed an exclusive lock
        assertTrue(clientA.trySharedLock(NODE, 1L));

        // Then I can upgrade it to exclusive
        assertTrue(clientA.tryExclusiveLock(NODE, 1L));

        // And other clients are denied it
        assertFalse(clientB.trySharedLock(NODE, 1L));
    }

    @Test
    void shouldUpgradeExclusiveOnTry() {
        // Given I've grabbed a shared lock
        clientA.acquireShared(LockTracer.NONE, NODE, 1L);

        // When
        assertTrue(clientA.tryExclusiveLock(NODE, 1L));

        // Then I should be able to release it
        clientA.releaseExclusive(NODE, 1L);
    }

    @Test
    void shouldAcquireMultipleSharedLocks() {
        clientA.acquireShared(LockTracer.NONE, NODE, 10, 100, 1000);

        assertFalse(clientB.tryExclusiveLock(NODE, 10));
        assertFalse(clientB.tryExclusiveLock(NODE, 100));
        assertFalse(clientB.tryExclusiveLock(NODE, 1000));

        assertEquals(3, lockCount());
    }

    @Test
    void shouldAcquireMultipleExclusiveLocks() {
        clientA.acquireExclusive(LockTracer.NONE, NODE, 10, 100, 1000);

        assertFalse(clientB.trySharedLock(NODE, 10));
        assertFalse(clientB.trySharedLock(NODE, 100));
        assertFalse(clientB.trySharedLock(NODE, 1000));

        assertEquals(3, lockCount());
    }

    @Test
    void shouldAcquireMultipleAlreadyAcquiredSharedLocks() {
        clientA.acquireShared(LockTracer.NONE, NODE, 10, 100, 1000);
        clientA.acquireShared(LockTracer.NONE, NODE, 100, 1000, 10000);

        assertFalse(clientB.tryExclusiveLock(NODE, 10));
        assertFalse(clientB.tryExclusiveLock(NODE, 100));
        assertFalse(clientB.tryExclusiveLock(NODE, 1000));
        assertFalse(clientB.tryExclusiveLock(NODE, 10000));

        assertEquals(4, lockCount());
    }

    @Test
    void shouldAcquireMultipleAlreadyAcquiredExclusiveLocks() {
        clientA.acquireExclusive(LockTracer.NONE, NODE, 10, 100, 1000);
        clientA.acquireExclusive(LockTracer.NONE, NODE, 100, 1000, 10000);

        assertFalse(clientB.trySharedLock(NODE, 10));
        assertFalse(clientB.trySharedLock(NODE, 100));
        assertFalse(clientB.trySharedLock(NODE, 1000));
        assertFalse(clientB.trySharedLock(NODE, 10000));

        assertEquals(4, lockCount());
    }

    @Test
    void shouldAcquireMultipleSharedLocksWhileHavingSomeExclusiveLocks() {
        clientA.acquireExclusive(LockTracer.NONE, NODE, 10, 100, 1000);
        clientA.acquireShared(LockTracer.NONE, NODE, 100, 1000, 10000);

        assertFalse(clientB.trySharedLock(NODE, 10));
        assertFalse(clientB.trySharedLock(NODE, 100));
        assertFalse(clientB.trySharedLock(NODE, 1000));
        assertFalse(clientB.tryExclusiveLock(NODE, 10000));

        assertEquals(4, lockCount());
    }

    @Test
    void shouldReleaseSharedLocksAcquiredInABatch() {
        clientA.acquireShared(LockTracer.NONE, NODE, 1, 10, 100);
        assertEquals(3, lockCount());

        clientA.releaseShared(NODE, 1);
        assertEquals(2, lockCount());

        clientA.releaseShared(NODE, 10);
        assertEquals(1, lockCount());

        clientA.releaseShared(NODE, 100);
        assertEquals(0, lockCount());
    }

    @Test
    void shouldReleaseExclusiveLocksAcquiredInABatch() {
        clientA.acquireExclusive(LockTracer.NONE, NODE, 1, 10, 100);
        assertEquals(3, lockCount());

        clientA.releaseExclusive(NODE, 1);
        assertEquals(2, lockCount());

        clientA.releaseExclusive(NODE, 10);
        assertEquals(1, lockCount());

        clientA.releaseExclusive(NODE, 100);
        assertEquals(0, lockCount());
    }

    @Test
    void releaseMultipleSharedLocks() {
        clientA.acquireShared(LockTracer.NONE, NODE, 10, 100, 1000);
        assertEquals(3, lockCount());

        clientA.releaseShared(NODE, 100, 1000);
        assertEquals(1, lockCount());

        assertFalse(clientB.tryExclusiveLock(NODE, 10));
        assertTrue(clientB.tryExclusiveLock(NODE, 100));
        assertTrue(clientB.tryExclusiveLock(NODE, 1000));
    }

    @Test
    void releaseMultipleExclusiveLocks() {
        clientA.acquireExclusive(LockTracer.NONE, NODE, 10, 100, 1000);

        assertFalse(clientB.trySharedLock(NODE, 10));
        assertFalse(clientB.trySharedLock(NODE, 100));
        assertFalse(clientB.trySharedLock(NODE, 1000));
        assertEquals(3, lockCount());

        clientA.releaseExclusive(NODE, 10, 100);
        assertEquals(1, lockCount());

        assertTrue(clientB.trySharedLock(NODE, 10));
        assertTrue(clientB.trySharedLock(NODE, 100));
        assertFalse(clientB.trySharedLock(NODE, 1000));
    }

    @Test
    void releaseMultipleAlreadyAcquiredSharedLocks() {
        clientA.acquireShared(LockTracer.NONE, NODE, 10, 100, 1000);
        clientA.acquireShared(LockTracer.NONE, NODE, 100, 1000, 10000);

        clientA.releaseShared(NODE, 100, 1000);
        assertEquals(4, lockCount());

        assertFalse(clientB.tryExclusiveLock(NODE, 100));
        assertFalse(clientB.tryExclusiveLock(NODE, 1000));

        clientA.releaseShared(NODE, 100, 1000);
        assertEquals(2, lockCount());
    }

    @Test
    void releaseMultipleAlreadyAcquiredExclusiveLocks() {
        clientA.acquireExclusive(LockTracer.NONE, NODE, 10, 100, 1000);
        clientA.acquireExclusive(LockTracer.NONE, NODE, 100, 1000, 10000);

        clientA.releaseExclusive(NODE, 100, 1000);
        assertEquals(4, lockCount());

        assertFalse(clientB.trySharedLock(NODE, 10));
        assertFalse(clientB.trySharedLock(NODE, 100));
        assertFalse(clientB.trySharedLock(NODE, 1000));
        assertFalse(clientB.trySharedLock(NODE, 10000));

        clientA.releaseExclusive(NODE, 100, 1000);

        assertEquals(2, lockCount());
    }

    @Test
    void releaseSharedLocksAcquiredSeparately() {
        clientA.acquireShared(LockTracer.NONE, NODE, 1);
        clientA.acquireShared(LockTracer.NONE, NODE, 2);
        clientA.acquireShared(LockTracer.NONE, NODE, 3);
        assertEquals(3, lockCount());

        assertFalse(clientB.tryExclusiveLock(NODE, 1));
        assertFalse(clientB.tryExclusiveLock(NODE, 2));
        assertFalse(clientB.tryExclusiveLock(NODE, 3));

        clientA.releaseShared(NODE, 1, 2, 3);

        assertEquals(0, lockCount());
        assertTrue(clientB.tryExclusiveLock(NODE, 1));
        assertTrue(clientB.tryExclusiveLock(NODE, 2));
        assertTrue(clientB.tryExclusiveLock(NODE, 3));
    }

    @Test
    void releaseExclusiveLocksAcquiredSeparately() {
        clientA.acquireExclusive(LockTracer.NONE, NODE, 1);
        clientA.acquireExclusive(LockTracer.NONE, NODE, 2);
        clientA.acquireExclusive(LockTracer.NONE, NODE, 3);
        assertEquals(3, lockCount());

        assertFalse(clientB.trySharedLock(NODE, 1));
        assertFalse(clientB.trySharedLock(NODE, 2));
        assertFalse(clientB.trySharedLock(NODE, 3));

        clientA.releaseExclusive(NODE, 1, 2, 3);

        assertEquals(0, lockCount());
        assertTrue(clientB.trySharedLock(NODE, 1));
        assertTrue(clientB.trySharedLock(NODE, 2));
        assertTrue(clientB.trySharedLock(NODE, 3));
    }

    @Test
    void releaseMultipleSharedLocksWhileHavingSomeExclusiveLocks() {
        clientA.acquireExclusive(LockTracer.NONE, NODE, 10, 100, 1000);
        clientA.acquireShared(LockTracer.NONE, NODE, 100, 1000, 10000);

        assertFalse(clientB.trySharedLock(NODE, 10));
        assertFalse(clientB.trySharedLock(NODE, 100));
        assertFalse(clientB.trySharedLock(NODE, 1000));
        assertFalse(clientB.tryExclusiveLock(NODE, 10000));
        assertEquals(4, lockCount());

        clientA.releaseShared(NODE, 100, 1000);

        assertFalse(clientB.trySharedLock(NODE, 10));
        assertFalse(clientB.trySharedLock(NODE, 100));
        assertFalse(clientB.trySharedLock(NODE, 1000));
        assertFalse(clientB.tryExclusiveLock(NODE, 10000));

        assertEquals(4, lockCount());
    }

    @Test
    void releaseMultipleExclusiveLocksWhileHavingSomeSharedLocks() {
        clientA.acquireShared(LockTracer.NONE, NODE, 100, 1000, 10000);
        clientA.acquireExclusive(LockTracer.NONE, NODE, 10, 100, 1000);

        assertFalse(clientB.trySharedLock(NODE, 10));
        assertFalse(clientB.trySharedLock(NODE, 100));
        assertFalse(clientB.trySharedLock(NODE, 1000));
        assertFalse(clientB.tryExclusiveLock(NODE, 10000));
        assertEquals(4, lockCount());

        clientA.releaseExclusive(NODE, 100, 1000);

        assertFalse(clientB.trySharedLock(NODE, 10));
        assertFalse(clientB.tryExclusiveLock(NODE, 100));
        assertFalse(clientB.tryExclusiveLock(NODE, 1000));
        assertFalse(clientB.tryExclusiveLock(NODE, 10000));

        assertEquals(4, lockCount());
    }

    private int lockCount() {
        LockCountVisitor lockVisitor = new LockCountVisitor();
        locks.accept(lockVisitor);
        return lockVisitor.getLockCount();
    }
}
