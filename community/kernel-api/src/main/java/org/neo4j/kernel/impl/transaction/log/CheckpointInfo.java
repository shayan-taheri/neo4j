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
package org.neo4j.kernel.impl.transaction.log;

import org.neo4j.kernel.KernelVersion;
import org.neo4j.kernel.KernelVersionProvider;
import org.neo4j.storageengine.api.StoreId;
import org.neo4j.storageengine.api.TransactionId;

/**
 * When want to tell if a checkpoint file is from an unsupported kernel version we have to create a CheckpointInfo with the raw kernel version code in order to
 * do the lookup (KernelVersion.getForVersion(byte version)) later.
 */
public record CheckpointInfo(
        LogPosition transactionLogPosition,
        StoreId storeId,
        LogPosition checkpointEntryPosition,
        LogPosition channelPositionAfterCheckpoint,
        LogPosition checkpointFilePostReadPosition,
        KernelVersion kernelVersion,
        byte kernelVersionByte,
        TransactionId transactionId,
        String reason)
        implements KernelVersionProvider {}
