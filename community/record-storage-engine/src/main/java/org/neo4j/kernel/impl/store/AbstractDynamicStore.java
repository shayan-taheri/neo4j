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
package org.neo4j.kernel.impl.store;

import static java.util.Objects.requireNonNull;
import static org.neo4j.memory.HeapEstimator.ARRAY_HEADER_BYTES;
import static org.neo4j.memory.HeapEstimator.alignObjectSize;

import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.eclipse.collections.api.set.ImmutableSet;
import org.neo4j.configuration.Config;
import org.neo4j.internal.helpers.collection.Iterables;
import org.neo4j.internal.id.IdGenerator;
import org.neo4j.internal.id.IdGeneratorFactory;
import org.neo4j.internal.id.IdType;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.pagecache.PageCache;
import org.neo4j.io.pagecache.PageCursor;
import org.neo4j.io.pagecache.context.CursorContext;
import org.neo4j.io.pagecache.tracing.PageCacheTracer;
import org.neo4j.kernel.impl.store.format.RecordFormat;
import org.neo4j.kernel.impl.store.record.DynamicRecord;
import org.neo4j.kernel.impl.store.record.Record;
import org.neo4j.logging.InternalLogProvider;
import org.neo4j.memory.MemoryTracker;
import org.neo4j.storageengine.api.cursor.StoreCursors;

/**
 * An abstract representation of a dynamic store. Record size is set at creation as the contents of the
 * first record and read and used when opening the store in future sessions.
 * <p>
 * Instead of a fixed record this class uses blocks to store a record. If a
 * record size is greater than the block size the record will use one or more
 * blocks to store its data.
 * <p>
 * A dynamic store don't have a {@link IdGenerator} because the position of a
 * record can't be calculated just by knowing the id. Instead, one should use
 * another store and store the start block of the record located in the
 * dynamic store. Note: This class makes use of an id generator internally for
 * managing free and non-free blocks.
 * <p>
 * Note, the first block of a dynamic store is reserved and contains information
 * about the store.
 * <p>
 * About configuring block size: Record size is the whole record size including the header (next pointer
 * and what not). The term block size is equivalent to data size, which is the size of the record - header size.
 * User configures block size and the block size is what is passed into the constructor to the store.
 * The record size is what's stored in the header (first record). {@link #getRecordDataSize()} returns
 * the size which was configured at the store creation, {@link #getRecordSize()} returns what the store header says.
 */
public abstract class AbstractDynamicStore extends CommonAbstractStore<DynamicRecord, IntStoreHeader> {
    public AbstractDynamicStore(
            FileSystemAbstraction fileSystem,
            Path path,
            Path idFile,
            Config conf,
            IdType idType,
            IdGeneratorFactory idGeneratorFactory,
            PageCache pageCache,
            PageCacheTracer pageCacheTracer,
            InternalLogProvider logProvider,
            String typeDescriptor,
            int dataSizeFromConfiguration,
            RecordFormat<DynamicRecord> recordFormat,
            boolean readOnly,
            String databaseName,
            ImmutableSet<OpenOption> openOptions) {
        super(
                fileSystem,
                path,
                idFile,
                conf,
                idType,
                idGeneratorFactory,
                pageCache,
                pageCacheTracer,
                logProvider,
                typeDescriptor,
                recordFormat,
                new DynamicStoreHeaderFormat(dataSizeFromConfiguration, recordFormat),
                readOnly,
                databaseName,
                openOptions);
    }

    public static void allocateRecordsFromBytes(
            Collection<DynamicRecord> recordList,
            byte[] src,
            DynamicRecordAllocator dynamicRecordAllocator,
            CursorContext cursorContext,
            MemoryTracker memoryTracker) {
        requireNonNull(src);

        int dataSize = dynamicRecordAllocator.getRecordDataSize();
        int payloadSize = src.length;
        int lastBlockSize = payloadSize % dataSize;

        long fullBlockSize = DynamicRecord.SHALLOW_SIZE + alignObjectSize(ARRAY_HEADER_BYTES + dataSize);
        int numberOfFullBlocks = payloadSize / dataSize;
        long totalSize = numberOfFullBlocks * fullBlockSize;

        if (lastBlockSize != 0) {
            totalSize += DynamicRecord.SHALLOW_SIZE + alignObjectSize(ARRAY_HEADER_BYTES + lastBlockSize);
        }

        memoryTracker.allocateHeap(totalSize);

        DynamicRecord nextRecord = dynamicRecordAllocator.nextRecord(cursorContext);
        int srcOffset = 0;
        do {
            DynamicRecord record = nextRecord;
            record.setStartRecord(srcOffset == 0);
            if (payloadSize - srcOffset > dataSize) {
                byte[] data = new byte[dataSize];
                System.arraycopy(src, srcOffset, data, 0, dataSize);
                record.setData(data);
                nextRecord = dynamicRecordAllocator.nextRecord(cursorContext);
                record.setNextBlock(nextRecord.getId());
                srcOffset += dataSize;
            } else {
                byte[] data = new byte[payloadSize - srcOffset];
                System.arraycopy(src, srcOffset, data, 0, data.length);
                record.setData(data);
                nextRecord = null;
                record.setNextBlock(Record.NO_NEXT_BLOCK.intValue());
            }
            recordList.add(record);
            assert record.getData() != null;
        } while (nextRecord != null);
    }

    /**
     * @return Pair&lt; header-in-first-record , all-other-bytes &gt;
     */
    public static HeavyRecordData readFullByteArrayFromHeavyRecords(
            Iterable<DynamicRecord> records, PropertyType propertyType) {
        byte[] header = null;
        List<byte[]> byteList = new ArrayList<>();
        int totalSize = 0;
        int i = 0;
        for (DynamicRecord record : records) {
            int offset = 0;
            if (i++ == 0) { // This is the first one, read out the header separately
                header = propertyType.readDynamicRecordHeader(record.getData());
                offset = header.length;
            }

            byteList.add(record.getData());
            totalSize += record.getData().length - offset;
        }
        byte[] bArray = new byte[totalSize];
        assert header != null
                : "header should be non-null since records should not be empty: " + Iterables.toString(records, ", ");
        int sourceOffset = header.length;
        int offset = 0;
        for (byte[] currentArray : byteList) {
            System.arraycopy(currentArray, sourceOffset, bArray, offset, currentArray.length - sourceOffset);
            offset += currentArray.length - sourceOffset;
            sourceOffset = 0;
        }
        return new HeavyRecordData(header, bArray);
    }

    @Override
    public String toString() {
        return super.toString() + "[fileName:" + storageFile.getFileName() + ", blockSize:" + getRecordDataSize() + "]";
    }

    HeavyRecordData readFullByteArray(
            Iterable<DynamicRecord> records, PropertyType propertyType, StoreCursors storeCursors) {
        for (DynamicRecord record : records) {
            ensureHeavy(record, storeCursors);
        }

        return readFullByteArrayFromHeavyRecords(records, propertyType);
    }

    private static class DynamicStoreHeaderFormat extends IntStoreHeaderFormat {
        DynamicStoreHeaderFormat(int dataSizeFromConfiguration, RecordFormat<DynamicRecord> recordFormat) {
            super(dataSizeFromConfiguration + recordFormat.getRecordHeaderSize());
        }

        @Override
        public void writeHeader(PageCursor cursor) {
            if (header < 1 || header > 0xFFFF) {
                throw new IllegalArgumentException("Illegal block size[" + header + "], limit is 65535");
            }
            super.writeHeader(cursor);
        }
    }

    record HeavyRecordData(byte[] header, byte[] data) {}
}
