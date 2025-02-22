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
package org.neo4j.kernel.impl.newapi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.neo4j.collection.Dependencies;
import org.neo4j.internal.kernel.api.TokenRead;
import org.neo4j.internal.kernel.api.security.SecurityContext;
import org.neo4j.internal.schema.SchemaState;
import org.neo4j.kernel.api.procedure.ProcedureView;
import org.neo4j.kernel.impl.api.KernelTransactionImplementation;
import org.neo4j.kernel.impl.api.index.IndexingService;
import org.neo4j.kernel.impl.api.index.stats.IndexStatisticsStore;
import org.neo4j.kernel.impl.api.state.TxState;
import org.neo4j.memory.EmptyMemoryTracker;
import org.neo4j.storageengine.api.StorageLocks;
import org.neo4j.storageengine.api.StorageNodeCursor;
import org.neo4j.storageengine.api.StorageReader;
import org.neo4j.storageengine.api.StorageRelationshipTraversalCursor;

class DefaultNodeCursorTest {
    @Test
    void hasLabelOnNewNodeDoesNotTouchStore() {
        final var NODEID = 1L;
        var read = buildReadState(txState -> txState.nodeDoCreate(NODEID));

        var storageCursor = mock(StorageNodeCursor.class);
        var storageRelCursor = mock(StorageRelationshipTraversalCursor.class);
        try (var defaultCursor = new DefaultNodeCursor((c) -> {}, storageCursor, storageCursor, storageRelCursor)) {
            defaultCursor.single(NODEID, read);
            assertTrue(defaultCursor.next());
            assertFalse(defaultCursor.hasLabel());
            // Should not have touched the store to verify that no label exists
            verify(storageCursor, never()).hasLabel();
        }
    }

    @Test
    void hasSpecifiedLabelOnNewNodeDoesNotTouchStore() {
        final var NODEID = 1L;
        var read = buildReadState(txState -> txState.nodeDoCreate(NODEID));

        var storageCursor = mock(StorageNodeCursor.class);
        var storageRelCursor = mock(StorageRelationshipTraversalCursor.class);
        try (var defaultCursor = new DefaultNodeCursor((c) -> {}, storageCursor, storageCursor, storageRelCursor)) {
            defaultCursor.single(NODEID, read);
            assertTrue(defaultCursor.next());
            assertFalse(defaultCursor.hasLabel(7));
            // Should not have touched the store to verify that the label exists
            verify(storageCursor, never()).hasLabel(7);
        }
    }

    private static Read buildReadState(Consumer<TxState> setup) {
        var ktx = mock(KernelTransactionImplementation.class);
        when(ktx.securityContext()).thenReturn(SecurityContext.AUTH_DISABLED);
        var read = new AllStoreHolder.ForTransactionScope(
                mock(StorageReader.class),
                mock(TokenRead.class),
                ktx,
                mock(StorageLocks.class),
                mock(DefaultPooledCursors.class),
                mock(SchemaState.class),
                mock(IndexingService.class),
                mock(IndexStatisticsStore.class),
                mock(Dependencies.class),
                EmptyMemoryTracker.INSTANCE);
        read.initialize(mock(ProcedureView.class));
        var txState = new TxState();
        setup.accept(txState);
        when(read.hasTxStateWithChanges()).thenReturn(true);
        when(read.txState()).thenReturn(txState);
        return read;
    }
}
