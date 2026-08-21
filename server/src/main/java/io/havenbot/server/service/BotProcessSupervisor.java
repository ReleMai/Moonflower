package io.havenbot.server.service;

import io.havenbot.server.config.RuntimeProperties;
import io.havenbot.server.model.BotRecord;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class BotProcessSupervisor {
    private static final Path BOT_LOG_DIR = Paths.get("../server-data/logs/bots");

    private final RuntimeProperties runtimeProperties;
    private final AccountService accountService;
    private final Map<UUID, RunningProcess> processes = new ConcurrentHashMap<>();

    public record LaunchDetails(
            String launchMode,
            String launchTarget,
            String workingDirectory,
            String logPath,
            List<String> command
    ) {
    }

    private record RunningProcess(Process process, LaunchDetails details) {
    }

    private record LaunchPlan(ProcessBuilder builder, LaunchDetails details) {
    }

    public BotProcessSupervisor(RuntimeProperties runtimeProperties, AccountService accountService) {
        this.runtimeProperties = runtimeProperties;
        this.accountService = accountService;
    }

    public LaunchDetails launch(BotRecord bot, String registrationToken) {
        if (isAlive(bot.id())) {
            throw new IllegalStateException("Bot process is already running.");
        }
        LaunchPlan plan = buildProcess(bot, registrationToken);
        try {
            Process process = plan.builder().start();
            processes.put(bot.id(), new RunningProcess(process, plan.details()));
            return plan.details();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to launch bot process.", ex);
        }
    }

    public void stop(UUID botId) {
        RunningProcess runningProcess = processes.remove(botId);
        if (runningProcess != null && runningProcess.process().isAlive()) {
            runningProcess.process().destroy();
        }
    }

    public boolean isTracked(UUID botId) {
        return processes.containsKey(botId);
    }

    public boolean isAlive(UUID botId) {
        RunningProcess runningProcess = processes.get(botId);
        return runningProcess != null && runningProcess.process().isAlive();
    }

    public Integer exitCode(UUID botId) {
        RunningProcess runningProcess = processes.get(botId);
        if (runningProcess == null || runningProcess.process().isAlive()) {
            return null;
        }
        return runningProcess.process().exitValue();
    }

    public Optional<LaunchDetails> describe(UUID botId) {
        RunningProcess runningProcess = processes.get(botId);
        if (runningProcess == null) {
            return Optional.empty();
        }
        return Optional.of(runningProcess.details());
    }

    public String readLogTail(UUID botId, int maxLines, int maxChars) {
        RunningProcess runningProcess = processes.get(botId);
        if (runningProcess == null) {
            return "";
        }
        return readLogTail(Paths.get(runningProcess.details().logPath()), maxLines, maxChars);
    }

    public void forget(UUID botId) {
        processes.remove(botId);
    }

    private LaunchPlan buildProcess(BotRecord bot, String registrationToken) {
        LaunchPlan plan = resolveLaunchPlan(bot);
        ProcessBuilder builder = plan.builder();
        builder.environment().put("HAVEN_BOT_ID", bot.id().toString());
        builder.environment().put("HAVEN_BOT_TOKEN", registrationToken);
        builder.environment().put("HAVEN_BOT_SERVER_URL", runtimeProperties.runtime().botServerUrl());
        builder.environment().put("HAVEN_BOT_CHARACTER", bot.preferredCharacter() == null ? "" : bot.preferredCharacter());
        builder.environment().put("HAVEN_BOT_PROFILE", bot.profileName() == null ? "" : bot.profileName());
        builder.environment().put("HAVEN_BOT_WORLD", bot.preferredWorld() == null ? "" : bot.preferredWorld());
        if (bot.accountId() != null) {
            accountService.get(bot.accountId()).ifPresent(account -> {
                builder.environment().put("HAVEN_ACCOUNT_NAME", account.name());
                builder.environment().put("HAVEN_ACCOUNT_USERNAME", account.username());
                builder.environment().put("HAVEN_ACCOUNT_SECRET", accountService.revealSecret(account.id()));
                builder.environment().put("HAVEN_ACCOUNT_CHARACTER", account.characterName() == null ? "" : account.characterName());
            });
        }
        Path logPath = configureLogging(bot, builder);
        builder.redirectErrorStream(true);
        return new LaunchPlan(
                builder,
                new LaunchDetails(
                        plan.details().launchMode(),
                        plan.details().launchTarget(),
                        plan.details().workingDirectory(),
                        logPath.toString(),
                        List.copyOf(builder.command())
                )
        );
    }

    private LaunchPlan resolveLaunchPlan(BotRecord bot) {
        String launchCommand = bot.launchCommand();
        if (launchCommand != null && !launchCommand.isBlank()) {
            Path workingDirectory = resolveWorkingDirectory(Paths.get(bot.clientInstallPath()));
            ProcessBuilder builder = new ProcessBuilder("cmd", "/c", launchCommand);
            if (Files.isDirectory(workingDirectory)) {
                builder.directory(workingDirectory.toFile());
            }
            return new LaunchPlan(
                    builder,
                    new LaunchDetails(
                            "custom-command",
                            launchCommand,
                            workingDirectory.toString(),
                            "",
                            List.of()
                    )
            );
        }

        Path installPath = Paths.get(bot.clientInstallPath()).toAbsolutePath().normalize();
        if (!Files.exists(installPath)) {
            throw new IllegalStateException("Client install path does not exist: " + installPath);
        }
        if (!Files.isDirectory(installPath)) {
            Path workingDirectory = resolveWorkingDirectory(installPath);
            ProcessBuilder builder = new ProcessBuilder("cmd", "/c", installPath.toString());
            if (workingDirectory != null && Files.isDirectory(workingDirectory)) {
                builder.directory(workingDirectory.toFile());
            }
            return new LaunchPlan(
                    builder,
                    new LaunchDetails(
                            "launcher-file",
                            installPath.toString(),
                            workingDirectory == null ? "" : workingDirectory.toString(),
                            "",
                            List.of()
                    )
            );
        }

        List<Path> candidates = new ArrayList<>();
        candidates.add(installPath.resolve("bin").resolve("Play.bat"));
        candidates.add(installPath.resolve("Play.bat"));

        for (Path candidate : candidates) {
            if (!Files.isRegularFile(candidate)) {
                continue;
            }
            Path workingDirectory = candidate.getParent() == null ? installPath : candidate.getParent().toAbsolutePath().normalize();
            ProcessBuilder builder = new ProcessBuilder("cmd", "/c", candidate.toString());
            builder.directory(workingDirectory.toFile());
            return new LaunchPlan(
                    builder,
                    new LaunchDetails(
                            candidate.startsWith(installPath.resolve("bin")) ? "launcher-script-bin" : "launcher-script",
                            candidate.toString(),
                            workingDirectory.toString(),
                            "",
                            List.of()
                    )
            );
        }

        String checkedPaths = candidates.stream()
                .map(Path::toString)
                .collect(Collectors.joining(", "));
        throw new IllegalStateException("No launcher script found under client install path. Checked: " + checkedPaths);
    }

    private Path resolveWorkingDirectory(Path clientInstallPath) {
        Path absolute = clientInstallPath.toAbsolutePath().normalize();
        if (Files.isDirectory(absolute)) {
            return absolute;
        }
        Path parent = absolute.getParent();
        return parent == null ? absolute : parent;
    }

    private Path configureLogging(BotRecord bot, ProcessBuilder builder) {
        try {
            Files.createDirectories(BOT_LOG_DIR);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create bot log directory.", ex);
        }
        Path logPath = BOT_LOG_DIR.resolve(bot.id() + ".log").toAbsolutePath().normalize();
        builder.redirectOutput(ProcessBuilder.Redirect.to(logPath.toFile()));
        return logPath;
    }

    private String readLogTail(Path logPath, int maxLines, int maxChars) {
        if (logPath == null || !Files.isRegularFile(logPath)) {
            return "";
        }
        try {
            List<String> lines = Files.readAllLines(logPath, StandardCharsets.UTF_8);
            if (lines.isEmpty()) {
                return "";
            }
            int fromIndex = Math.max(lines.size() - Math.max(maxLines, 1), 0);
            List<String> tailLines = lines.subList(fromIndex, lines.size());
            String tail = String.join(System.lineSeparator(), tailLines).trim();
            if (tail.length() <= maxChars) {
                return tail;
            }
            return tail.substring(tail.length() - maxChars).trim();
        } catch (IOException ex) {
            return "";
        }
    }
}
