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
package org.neo4j.kernel.impl.store.format.standard;

import org.neo4j.kernel.impl.store.format.BaseRecordFormats;
import org.neo4j.kernel.impl.store.format.FormatFamily;
import org.neo4j.kernel.impl.store.format.RecordFormat;
import org.neo4j.kernel.impl.store.format.RecordFormatFamilyCapability;
import org.neo4j.kernel.impl.store.format.RecordFormats;
import org.neo4j.kernel.impl.store.format.StoreVersion;
import org.neo4j.kernel.impl.store.record.DynamicRecord;
import org.neo4j.kernel.impl.store.record.LabelTokenRecord;
import org.neo4j.kernel.impl.store.record.NodeRecord;
import org.neo4j.kernel.impl.store.record.PropertyKeyTokenRecord;
import org.neo4j.kernel.impl.store.record.PropertyRecord;
import org.neo4j.kernel.impl.store.record.RelationshipGroupRecord;
import org.neo4j.kernel.impl.store.record.RelationshipRecord;
import org.neo4j.kernel.impl.store.record.RelationshipTypeTokenRecord;
import org.neo4j.kernel.impl.store.record.SchemaRecord;

public class StandardV5_0 extends BaseRecordFormats {
    public static final RecordFormats RECORD_FORMATS = new StandardV5_0();
    public static final String NAME = FormatFamily.STANDARD.name();

    public StandardV5_0() {
        super(StoreVersion.STANDARD_V5_0, new RecordFormatFamilyCapability(FormatFamily.STANDARD));
    }

    @Override
    public RecordFormat<NodeRecord> node() {
        return new NodeRecordFormat();
    }

    @Override
    public RecordFormat<RelationshipGroupRecord> relationshipGroup() {
        return new RelationshipGroupRecordFormat();
    }

    @Override
    public RecordFormat<RelationshipRecord> relationship() {
        return new RelationshipRecordFormat();
    }

    @Override
    public RecordFormat<PropertyRecord> property() {
        return new PropertyRecordFormat();
    }

    @Override
    public RecordFormat<LabelTokenRecord> labelToken() {
        return new LabelTokenRecordFormat();
    }

    @Override
    public RecordFormat<PropertyKeyTokenRecord> propertyKeyToken() {
        return new PropertyKeyTokenRecordFormat();
    }

    @Override
    public RecordFormat<RelationshipTypeTokenRecord> relationshipTypeToken() {
        return new RelationshipTypeTokenRecordFormat();
    }

    @Override
    public RecordFormat<DynamicRecord> dynamic() {
        return new DynamicRecordFormat();
    }

    @Override
    public FormatFamily getFormatFamily() {
        return FormatFamily.STANDARD;
    }

    @Override
    public RecordFormat<SchemaRecord> schema() {
        return new SchemaRecordFormat();
    }

    @Override
    public String name() {
        return NAME;
    }
}
