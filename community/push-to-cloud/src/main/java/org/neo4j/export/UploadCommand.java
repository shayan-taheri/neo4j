/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.neo4j.export;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipException;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.neo4j.cli.AbstractAdminCommand;
import org.neo4j.cli.CommandFailedException;
import org.neo4j.cli.Converters;
import org.neo4j.cli.ExecutionContext;
import org.neo4j.dbms.archive.Dumper;
import org.neo4j.dbms.archive.Loader;
import org.neo4j.io.ByteUnit;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.memory.NativeScopedBuffer;
import org.neo4j.kernel.database.NormalizedDatabaseName;
import org.neo4j.memory.EmptyMemoryTracker;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
        name = "upload",
        description = "Push a local database to a Neo4j Aura instance. "
                + "The target location is a Neo4j Aura Bolt URI. If Neo4j Cloud username and password are not provided "
                + "either as a command option or as an environment variable, they will be requested interactively ")
public class UploadCommand extends AbstractAdminCommand {
    private static final long CRC32_BUFFER_SIZE = ByteUnit.mebiBytes(4);
    private static final String DEV_MODE_VAR_NAME = "P2C_DEV_MODE";
    private static final String ENV_NEO4J_USERNAME = "NEO4J_USERNAME";
    private static final String ENV_NEO4J_PASSWORD = "NEO4J_PASSWORD";
    private final PushToCloudCLI pushToCloudCLI;

    @Parameters(
            paramLabel = "<database>",
            description = "Name of the database that should be uploaded. The name is used to select a dump file "
                    + "which is expected to be named <database>.dump.",
            converter = Converters.DatabaseNameConverter.class)
    private NormalizedDatabaseName database;

    @Option(
            names = "--from-path",
            paramLabel = "<path>",
            description =
                    "'/path/to/directory-containing-dump' Path to a directory containing a database dump to upload.",
            required = true)
    private Path dumpDirectory;

    @Option(
            names = "--to-uri",
            paramLabel = "<uri>",
            arity = "1",
            required = true,
            description = "'neo4j://mydatabaseid.databases.neo4j.io' Bolt URI of the target database.")
    private String boltURI;

    @Option(
            names = "--to-user",
            defaultValue = "${" + ENV_NEO4J_USERNAME + "}",
            description =
                    "Username of the target database to push this database to. Prompt will ask for a username if not provided. "
                            + "Alternatively, the " + ENV_NEO4J_USERNAME + " environment variable can be used.")
    private String username;

    private static final String TO_PASSWORD = "--to-password";

    @Option(
            names = TO_PASSWORD,
            defaultValue = "${" + ENV_NEO4J_PASSWORD + "}",
            description =
                    "Password of the target database to push this database to. Prompt will ask for a password if not provided. "
                            + "Alternatively, the " + ENV_NEO4J_PASSWORD + " environment variable can be used.")
    private String password;

    @Option(
            names = "--overwrite-destination",
            arity = "0..1",
            paramLabel = "true|false",
            fallbackValue = "true",
            showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
            description = "Overwrite the data in the target database.")
    private boolean overwrite;

    @Option(
            names = "--to",
            paramLabel = "<destination>",
            description = "The destination for the upload.",
            defaultValue = "aura",
            showDefaultValue = CommandLine.Help.Visibility.ALWAYS)
    private String to;

    private final org.neo4j.export.AuraClient.AuraClientBuilder clientBuilder;

    private final AuraURLFactory auraURLFactory;

    private final UploadURLFactory uploadURLFactory;

    public UploadCommand(
            ExecutionContext ctx,
            org.neo4j.export.AuraClient.AuraClientBuilder clientBuilder,
            AuraURLFactory auraURLFactory,
            UploadURLFactory uploadURLFactory,
            PushToCloudCLI pushToCloudCLI) {
        super(ctx);
        this.clientBuilder = clientBuilder;
        this.pushToCloudCLI = pushToCloudCLI;
        this.auraURLFactory = auraURLFactory;
        this.uploadURLFactory = uploadURLFactory;
    }

    public static long readSizeFromDumpMetaData(ExecutionContext ctx, Path dump) {
        Loader.DumpMetaData metaData;
        try {
            final var fileSystem = ctx.fs();
            metaData = new Loader(fileSystem, System.out).getMetaData(() -> fileSystem.openAsInputStream(dump));
        } catch (IOException e) {
            throw new CommandFailedException("Unable to check size of database dump.", e);
        }
        return Long.parseLong(metaData.byteCount());
    }

    public long readSizeFromTarMetaData(ExecutionContext ctx, Path tar, String dbName) {
        final var fileSystem = ctx.fs();

        try (TarArchiveInputStream tais = new TarArchiveInputStream(maybeGzipped(tar, fileSystem))) {
            Loader.DumpMetaData metaData;
            TarArchiveEntry entry;
            while ((entry = tais.getNextTarEntry()) != null) {
                if (entry.getName().endsWith(dbName + ".dump")) {

                    metaData = new Loader(fileSystem, System.out).getMetaData(() -> tais);
                    return Long.parseLong(metaData.byteCount());
                }
            }
            throw new CommandFailedException(
                    String.format("TAR file %s does not contain dump for  database %s", tar, dbName));
        } catch (IOException e) {
            throw new CommandFailedException("Unable to check size of tar dump database.", e);
        }
    }

    private InputStream maybeGzipped(Path tar, final FileSystemAbstraction fileSystem) throws IOException {
        try {
            return new GZIPInputStream(fileSystem.openAsInputStream(tar));
        } catch (ZipException e) {
            return fileSystem.openAsInputStream(tar);
        }
    }

    public static String sizeText(long size) {
        return format("%.1f GB", bytesToGibibytes(size));
    }

    public static double bytesToGibibytes(long sizeInBytes) {
        return sizeInBytes / (double) (1024 * 1024 * 1024);
    }

    @Override
    public void execute() {
        try {
            if (!"aura".equals(to)) {
                throw new CommandFailedException(
                        format("'%s' is not a supported destination. Supported destinations are: 'aura'", to));
            }

            if (isBlank(username)) {
                if (isBlank(username = pushToCloudCLI.readLine("%s", "Neo4j aura username (default: neo4j):"))) {
                    username = "neo4j";
                }
            }
            char[] pass;
            if (isBlank(password)) {
                if ((pass = pushToCloudCLI.readPassword("Neo4j aura password for %s:", username)).length == 0) {
                    throw new CommandFailedException(format(
                            "Please supply a password, either by '%s' parameter, '%s' environment variable, or prompt",
                            TO_PASSWORD, ENV_NEO4J_PASSWORD));
                }
            } else {
                pass = password.toCharArray();
            }

            boolean devMode = pushToCloudCLI.readDevMode(DEV_MODE_VAR_NAME);

            String consoleURL = auraURLFactory.buildConsoleURI(boltURI, devMode);

            AuraClient auraClient = clientBuilder
                    .withConsoleURL(consoleURL)
                    .withUserName(username)
                    .withPassword(pass)
                    .withConsent(overwrite)
                    .withBoltURI(boltURI)
                    .withDefaults()
                    .build();

            Uploader uploader = makeDumpUploader(dumpDirectory, database.name());

            uploader.process(auraClient);
        } catch (Exception e) {
            throw new CommandFailedException(e.getMessage());
        }
    }

    private void verbose(String format, Object... args) {
        if (verbose) {
            ctx.out().printf(format, args);
        }
    }

    public DumpUploader makeDumpUploader(Path dump, String database) {
        if (!ctx.fs().isDirectory(dump)) {
            throw new CommandFailedException(format("The provided source directory '%s' doesn't exist", dump));
        }
        Path dumpFile = dump.resolve(database + Dumper.DUMP_EXTENSION);
        if (!ctx.fs().fileExists(dumpFile)) {
            Path tarFile = dump.resolve(database + Dumper.TAR_EXTENSION);
            if (!ctx.fs().fileExists(tarFile)) {
                throw new CommandFailedException(format(
                        "Dump files '%s' or '%s' do not exist", dumpFile.toAbsolutePath(), tarFile.toAbsolutePath()));
            }
            dumpFile = tarFile;
        }
        return new DumpUploader(new Source(ctx.fs(), dumpFile, dumpSize(dumpFile, database)));
    }

    private long dumpSize(Path dump, String database) {
        long sizeInBytes;
        if (dump.getFileName().toString().endsWith(".dump")) {
            sizeInBytes = readSizeFromDumpMetaData(ctx, dump);
        } else {
            sizeInBytes = readSizeFromTarMetaData(ctx, dump, database);
        }
        verbose("Determined DumpSize=%d bytes from dump at %s\n", sizeInBytes, dump);
        return sizeInBytes;
    }

    abstract static class Uploader {
        protected final Source source;

        Uploader(Source source) {
            this.source = source;
        }

        long size() {
            return source.size();
        }

        abstract void process(AuraClient auraClient);
    }

    public record Source(FileSystemAbstraction fs, Path path, long size) {
        long crc32Sum() throws IOException {
            CRC32 crc = new CRC32();
            try (var channel = fs.read(path);
                    var buffer = new NativeScopedBuffer(
                            CRC32_BUFFER_SIZE, ByteOrder.LITTLE_ENDIAN, EmptyMemoryTracker.INSTANCE)) {
                var byteBuffer = buffer.getBuffer();
                while ((channel.read(byteBuffer)) != -1) {
                    byteBuffer.flip();
                    crc.update(byteBuffer);
                    byteBuffer.clear();
                }
            }
            return crc.getValue();
        }
    }

    class DumpUploader extends Uploader {
        DumpUploader(Source source) {
            super(source);
        }

        @Override
        void process(AuraClient auraClient) {
            // Check size of dump (reading actual database size from dump header)
            verbose("Checking database size %s fits at %s\n", sizeText(size()), auraClient.getConsoleURL());

            String bearerToken = auraClient.authenticate(verbose);
            auraClient.checkSize(verbose, size(), bearerToken);

            // Upload dumpFile
            verbose("Uploading data of %s to %s\n", sizeText(size()), auraClient.getConsoleURL());

            String version = getClass().getPackage().getImplementationVersion();
            long crc32Sum;
            try {
                crc32Sum = source.crc32Sum();

            } catch (IOException e) {
                throw new CommandFailedException("Failed to calculate CRC32 checksum of dump file", e);
            }

            AuraResponse.SignedURIBody signedURIBody =
                    auraClient.initatePresignedUpload(crc32Sum, size(), bearerToken, version);
            SignedUpload signedUpload = uploadURLFactory.fromAuraResponse(signedURIBody, ctx, boltURI);
            signedUpload.copy(verbose, source);

            try {
                ctx.out().println("Triggering import");
                auraClient.triggerImportProtocol(verbose, source.path(), crc32Sum, bearerToken);
                verbose("Polling status\n");
                auraClient.doStatusPolling(verbose, bearerToken, source.size());
            } catch (IOException e) {
                throw new CommandFailedException("Failed to trigger import, please contact Aura support", e);
            } catch (InterruptedException e) {
                throw new CommandFailedException("Command interrupted", e);
            }

            ctx.out().println("Dump successfully uploaded to Aura");
            ctx.out().println(String.format("Your dump at %s can now be deleted.", source.path()));
        }
    }
}
