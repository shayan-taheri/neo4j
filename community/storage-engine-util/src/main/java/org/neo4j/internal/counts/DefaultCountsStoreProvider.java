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
package org.neo4j.internal.counts;

import static org.neo4j.configuration.GraphDatabaseInternalSettings.counts_store_max_cached_entries;

import java.io.IOException;
import java.nio.file.OpenOption;
import org.eclipse.collections.api.set.ImmutableSet;
import org.neo4j.annotations.service.ServiceProvider;
import org.neo4j.configuration.Config;
import org.neo4j.counts.CountsStore;
import org.neo4j.exceptions.UnderlyingStorageException;
import org.neo4j.index.internal.gbptree.RecoveryCleanupWorkCollector;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.layout.recordstorage.RecordDatabaseLayout;
import org.neo4j.io.pagecache.PageCache;
import org.neo4j.io.pagecache.context.CursorContextFactory;
import org.neo4j.io.pagecache.tracing.PageCacheTracer;
import org.neo4j.logging.InternalLogProvider;

@ServiceProvider
public class DefaultCountsStoreProvider implements CountsStoreProvider {

    @Override
    public CountsStore openCountsStore(
            PageCache pageCache,
            FileSystemAbstraction fs,
            RecordDatabaseLayout layout,
            InternalLogProvider userLogProvider,
            RecoveryCleanupWorkCollector recoveryCleanupWorkCollector,
            Config config,
            CursorContextFactory contextFactory,
            PageCacheTracer pageCacheTracer,
            ImmutableSet<OpenOption> openOptions,
            CountsBuilder initialCountsBuilder) {
        try {
            return new GBPTreeCountsStore(
                    pageCache,
                    layout.countStore(),
                    fs,
                    recoveryCleanupWorkCollector,
                    initialCountsBuilder,
                    false,
                    GBPTreeGenericCountsStore.NO_MONITOR,
                    layout.getDatabaseName(),
                    config.get(counts_store_max_cached_entries),
                    userLogProvider,
                    contextFactory,
                    pageCacheTracer,
                    openOptions);
        } catch (IOException e) {
            throw new UnderlyingStorageException(e);
        }
    }

    @Override
    public int getPriority() {
        return 1000;
    }
}
