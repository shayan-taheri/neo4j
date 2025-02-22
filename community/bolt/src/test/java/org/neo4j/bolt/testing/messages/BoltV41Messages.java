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
package org.neo4j.bolt.testing.messages;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.neo4j.bolt.negotiation.ProtocolVersion;
import org.neo4j.bolt.protocol.common.message.AccessMode;
import org.neo4j.bolt.protocol.common.message.request.RequestMessage;
import org.neo4j.bolt.protocol.v41.BoltProtocolV41;
import org.neo4j.bolt.testing.error.UnsupportedProtocolFeatureException;

/**
 * Quick access of all Bolt V41 messages
 */
public class BoltV41Messages extends AbstractBoltMessages {
    private static final BoltV41Messages INSTANCE = new BoltV41Messages();

    protected BoltV41Messages() {}

    public static BoltMessages getInstance() {
        return INSTANCE;
    }

    @Override
    public ProtocolVersion version() {
        return BoltProtocolV41.VERSION;
    }

    @Override
    public RequestMessage authenticate(String principal, String credentials) {
        return this.hello(principal, credentials);
    }

    @Override
    public RequestMessage logon() {
        throw new UnsupportedProtocolFeatureException("Logon");
    }

    @Override
    public RequestMessage logon(String principal, String credentials) {
        throw new UnsupportedProtocolFeatureException("Logon");
    }

    @Override
    public RequestMessage logoff() {
        throw new UnsupportedProtocolFeatureException("Logoff");
    }

    @Override
    public RequestMessage route() {
        throw new UnsupportedProtocolFeatureException("Routing");
    }

    @Override
    public RequestMessage begin(
            List<String> bookmarks,
            Duration txTimeout,
            AccessMode mode,
            Map<String, Object> txMetadata,
            String databaseName,
            String impersonatedUser) {
        if (impersonatedUser != null) {
            throw new UnsupportedProtocolFeatureException("Impersonation");
        }

        return super.begin(bookmarks, txTimeout, mode, txMetadata, databaseName, impersonatedUser);
    }
}
