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
package org.neo4j.server.startup;

import static org.neo4j.server.startup.Bootloader.ARG_EXPAND_COMMANDS;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.neo4j.cli.AbstractCommand;
import org.neo4j.cli.AdminTool;
import org.neo4j.cli.CommandFailedException;
import org.neo4j.cli.ExecutionContext;
import org.neo4j.io.fs.DefaultFileSystemAbstraction;
import org.neo4j.kernel.internal.Version;
import org.neo4j.util.VisibleForTesting;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "Neo4j Admin", description = "Neo4j Admin CLI.")
public class Neo4jAdminCommand implements Callable<Integer>, VerboseCommand {

    private final Class<?> entrypoint;
    private final Environment environment;

    // unmatched parameters.
    @Parameters(hidden = true)
    private List<String> unmatchedParameters = List.of();

    @Option(
            names = ARG_EXPAND_COMMANDS,
            hidden = true,
            description = "Allow command expansion in config value evaluation.")
    boolean expandCommands;

    @Option(names = ARG_VERBOSE, hidden = true, description = "Prints additional information.")
    boolean verbose;

    public Neo4jAdminCommand(Environment environment) {
        this(AdminTool.class, environment);
    }

    protected Neo4jAdminCommand(Class<?> entrypoint, Environment environment) {
        this.entrypoint = entrypoint;
        this.environment = environment;
    }

    @Override
    public Integer call() throws Exception {
        String[] args = buildArgs();

        try (var adminBootloader = createAdminBootloader(args)) {

            // Lets verify our arguments before we try to execute the command, avoiding forking the VM if the arguments
            // are
            // invalid and improves error/help messages
            var ctx = new EnhancedExecutionContext(
                    adminBootloader.home(),
                    adminBootloader.confDir(),
                    environment.out(),
                    environment.err(),
                    new DefaultFileSystemAbstraction(),
                    this::createDbmsBootloader);
            CommandLine actualAdminCommand = getActualAdminCommand(ctx);

            if (unmatchedParameters.isEmpty()) { // No arguments (except expand commands/verbose), print usage
                actualAdminCommand.usage(adminBootloader.environment.err());
                return CommandLine.ExitCode.USAGE;
            }

            boolean shouldFork;
            try {
                CommandLine.ParseResult result = actualAdminCommand.parseArgs(args); // Check if we can parse it
                Integer code = CommandLine.executeHelpRequest(result); // If help is requested
                if (code != null) {
                    return code;
                }
                shouldFork = shouldFork(result);
            } catch (CommandLine.ParameterException e) {
                return e.getCommandLine()
                        .getParameterExceptionHandler()
                        .handleParseException(e, args); // Parse error, handle and exit
            }

            // Arguments looks fine! Let's try to execute it for real
            if (shouldFork) {
                return adminBootloader.admin();
            } else {

                return actualAdminCommand.execute(args);
            }
        }
    }

    private String[] buildArgs() {
        List<String> allParameters = new ArrayList<>(unmatchedParameters);
        if (expandCommands) {
            allParameters.add(ARG_EXPAND_COMMANDS);
        }
        if (verbose) {
            allParameters.add(ARG_VERBOSE);
        }
        return allParameters.toArray(new String[0]);
    }

    // Admin commands are generally forked, because it is the only way how their JVM can configured.
    // Some admin commands are used to just launch the DBMS instead of executing an administration task.
    // As an optimisation, such commands should not fork, because we might end up with
    // three running JVMs if they do - boostrap JVM, forked command JVM and DBMS JVM.
    private boolean shouldFork(CommandLine.ParseResult parseResult) {
        return parseResult.asCommandLineList().stream()
                .map(CommandLine::getCommand)
                .anyMatch(command -> command instanceof AbstractCommand && ((AbstractCommand) command).shouldFork());
    }

    protected CommandLine getActualAdminCommand(ExecutionContext executionContext) {
        return AdminTool.getCommandLine(executionContext);
    }

    @VisibleForTesting
    protected Bootloader.Admin createAdminBootloader(String[] args) {
        return new Bootloader.Admin(entrypoint, environment, expandCommands, verbose, args);
    }

    @VisibleForTesting
    protected Bootloader.Dbms createDbmsBootloader() {
        return new Bootloader.Dbms(environment, expandCommands, verbose);
    }

    @Override
    public boolean verbose() {
        return verbose;
    }

    static CommandLine asCommandLine(Neo4jAdminCommand command, Environment environment) {
        return new CommandLine(command)
                .setCaseInsensitiveEnumValuesAllowed(true)
                .setExecutionExceptionHandler(new ExceptionHandler(environment))
                .setOut(new PrintWriter(environment.out(), true))
                .setErr(new PrintWriter(environment.err(), true))
                .setUnmatchedArgumentsAllowed(true)
                .setUnmatchedOptionsArePositionalParams(true)
                .setExpandAtFiles(false)
                .addSubcommand("version", new VersionCommand(environment.out()), "--version");
    }

    public static void main(String[] args) {
        var environment = Environment.SYSTEM;
        int exitCode = Neo4jAdminCommand.asCommandLine(new Neo4jAdminCommand(environment), environment)
                .execute(args);
        System.exit(exitCode);
    }

    private static class ExceptionHandler implements CommandLine.IExecutionExceptionHandler {
        private final Environment environment;

        ExceptionHandler(Environment environment) {
            this.environment = environment;
        }

        @Override
        public int handleExecutionException(
                Exception exception, CommandLine commandLine, CommandLine.ParseResult parseResult) {
            if (commandLine.getCommand() instanceof VerboseCommand
                    && !((VerboseCommand) commandLine.getCommand()).verbose()) {
                environment.err().println(exception.getMessage());
                environment.err().println("Run with '--verbose' for a more detailed error message.");
            } else {
                exception.printStackTrace(environment.err());
            }
            if (exception instanceof CommandFailedException failure) {
                return failure.getExitCode();
            }
            return commandLine.getCommandSpec().exitCodeOnExecutionException();
        }
    }

    @CommandLine.Command(name = "version", description = "Print version information and exit.")
    static class VersionCommand implements Callable<Integer> {

        private final PrintStream out;

        protected VersionCommand(PrintStream out) {
            this.out = out;
        }

        @Override
        public Integer call() throws Exception {
            out.println("neo4j " + Version.getNeo4jVersion());
            return 0;
        }
    }
}
