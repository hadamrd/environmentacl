package io.jenkins.plugins.pulsar.ssh.service;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.slaves.ComputerListener;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

@Extension
public class SshAgentDiscovery extends ComputerListener {
    private static final Logger LOGGER = Logger.getLogger(SshAgentDiscovery.class.getName());

    @Override
    public void onOnline(Computer computer, TaskListener listener) {
        new Thread(() -> {
                    try {
                        String nodeName = computer.getName();
                        LOGGER.info("Starting ssh discovery for node: " + nodeName);

                        hudson.Launcher launcher;
                        if (computer == Jenkins.get().toComputer()) {
                            launcher = new hudson.Launcher.LocalLauncher(listener);
                        } else if (computer.getChannel() != null) {
                            launcher = new hudson.Launcher.RemoteLauncher(listener, computer.getChannel(), false);
                        } else {
                            LOGGER.warning(
                                    "No channel available for node: " + nodeName + ", skipping ssh-agent discovery");
                            return;
                        }

                        discoverAndAdoptSshAgents(nodeName, launcher, listener);
                        LOGGER.info("Ssh-Agent discovery completed for node: " + nodeName);

                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Failed to discover ssh-agents for node: " + computer.getName(), e);
                    }
                })
                .start();
    }

    /** Main discovery method - cleans up orphaned files, zombies, and maintains singleton */
    private void discoverAndAdoptSshAgents(String nodeName, hudson.Launcher launcher, TaskListener listener) {
        try {
            String nodeFilter = nodeName != null && !nodeName.trim().isEmpty() ? nodeName : "master";

            // Check if we already have an active SSH agent for this node
            SshAgent existingAgent = SshAgent.getInstance(nodeFilter);
            if (existingAgent.getSocketPath() != null && existingAgent.isRunning(launcher, listener)) {
                LOGGER.info("SSH agent already tracked and running for node: " + nodeFilter);
                return;
            }

            // Step 1: Get all processes and categorize them
            Map<String, String> activePidToSocket = new HashMap<>();
            List<String> zombiePids = new ArrayList<>();
            categorizeAgentProcesses(nodeFilter, activePidToSocket, zombiePids, launcher, listener);

            // Step 3: Clean up orphaned socket files
            cleanupOrphanedSockets(nodeFilter, activePidToSocket, launcher, listener);

            // Step 4: Handle active agents - adopt one, kill extras
            handleActiveAgents(nodeFilter, activePidToSocket, launcher, listener);

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to discover SSH agents", e);
        }
    }

    /** Categorize all ssh-agent processes into active vs zombie */
    private void categorizeAgentProcesses(
            String nodeFilter,
            Map<String, String> activePidToSocket,
            List<String> zombiePids,
            hudson.Launcher launcher,
            TaskListener listener) {
        try {
            List<String> getAgentsCmd = Arrays.asList(
                    "/bin/sh",
                    "-c",
                    "for pid in /proc/*/comm; do "
                            + "if [ -f \"$pid\" ] && [ \"$(cat \"$pid\" 2>/dev/null)\" = \"ssh-agent\" ]; then "
                            + "pid_num=$(echo \"$pid\" | sed 's|/proc/||; s|/comm||'); "
                            + "if [ -f \"/proc/$pid_num/stat\" ]; then "
                            + "state=$(awk '{print $3}' \"/proc/$pid_num/stat\" 2>/dev/null); "
                            + "if [ \"$state\" = \"Z\" ]; then "
                            + "echo \"ZOMBIE:$pid_num\"; "
                            + "elif [ -f \"/proc/$pid_num/cmdline\" ]; then "
                            + "cmdline=$(cat \"/proc/$pid_num/cmdline\" 2>/dev/null | tr '\\0' ' '); "
                            + "echo \"ACTIVE:$pid_num:$cmdline\"; "
                            + "fi; fi; fi; done");

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            int exitCode = launcher.launch()
                    .cmds(getAgentsCmd)
                    .stdout(output)
                    .stderr(output)
                    .quiet(true)
                    .start()
                    .joinWithTimeout(15, TimeUnit.SECONDS, listener);

            if (exitCode == 0) {
                String result = output.toString(StandardCharsets.UTF_8).trim();
                if (!result.isEmpty()) {
                    String[] lines = result.split("\n");
                    for (String line : lines) {
                        if (line.startsWith("ZOMBIE:")) {
                            String pid = line.substring(7);
                            zombiePids.add(pid);
                        } else if (line.startsWith("ACTIVE:")) {
                            String[] parts = line.substring(7).split(":", 2);
                            if (parts.length == 2) {
                                String pid = parts[0];
                                String cmdline = parts[1];
                                String socketPath = extractSocketFromCmdline(cmdline);

                                if (socketPath != null && socketPath.contains("agent-" + nodeFilter + "-")) {
                                    activePidToSocket.put(pid, socketPath);
                                    LOGGER.info("Found active ssh-agent: PID " + pid + " socket " + socketPath);
                                }
                            }
                        }
                    }
                }
            }

            LOGGER.info("Categorized: " + activePidToSocket.size() + " active, " + zombiePids.size() + " zombies");

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to categorize agent processes", e);
        }
    }

    /** Clean up socket files that don't have active processes */
    private void cleanupOrphanedSockets(
            String nodeFilter, Map<String, String> activePidToSocket, hudson.Launcher launcher, TaskListener listener) {
        try {
            List<String> findCmd = Arrays.asList(
                    "/bin/sh",
                    "-c",
                    String.format(
                            "find %s -name 'agent-%s-*.sock' 2>/dev/null || true",
                            SshAgent.getSshAgentsDir(), nodeFilter));

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            int exitCode = launcher.launch()
                    .cmds(findCmd)
                    .stdout(output)
                    .stderr(output)
                    .quiet(true)
                    .start()
                    .joinWithTimeout(10, TimeUnit.SECONDS, listener);

            if (exitCode == 0) {
                String result = output.toString(StandardCharsets.UTF_8).trim();
                if (!result.isEmpty()) {
                    String[] socketPaths = result.split("\n");
                    Set<String> activeSockets = new HashSet<>(activePidToSocket.values());

                    int removedCount = 0;
                    for (String socketPath : socketPaths) {
                        if (socketPath.trim().isEmpty()) continue;

                        if (!activeSockets.contains(socketPath.trim())) {
                            List<String> removeCmd = Arrays.asList("rm", "-f", socketPath.trim());
                            launcher.launch()
                                    .cmds(removeCmd)
                                    .quiet(true)
                                    .start()
                                    .joinWithTimeout(5, TimeUnit.SECONDS, listener);

                            removedCount++;
                            LOGGER.info("Removed orphaned socket: " + socketPath);
                        }
                    }

                    if (removedCount > 0) {
                        LOGGER.info("Cleaned up " + removedCount + " orphaned socket files");
                    }
                }
            }

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to cleanup orphaned sockets", e);
        }
    }

    /** Handle active agents - adopt one, kill the rest to maintain singleton */
    private void handleActiveAgents(
            String nodeFilter, Map<String, String> activePidToSocket, hudson.Launcher launcher, TaskListener listener) {
        if (activePidToSocket.isEmpty()) {
            LOGGER.info("No active SSH agents found for node: " + nodeFilter);
            return;
        }

        // Pick the first active agent to adopt
        String adoptedPid = activePidToSocket.keySet().iterator().next();
        String adoptedSocket = activePidToSocket.get(adoptedPid);

        // Adopt the chosen agent
        adoptSshAgent(nodeFilter, adoptedSocket, adoptedPid, launcher, listener);

        // Kill the rest to maintain singleton
        int killedCount = 0;
        for (Map.Entry<String, String> entry : activePidToSocket.entrySet()) {
            if (!entry.getKey().equals(adoptedPid)) {
                if (forceKillAgent(entry.getKey(), launcher, listener)) {
                    killedCount++;
                }
            }
        }

        LOGGER.info("Adopted 1 SSH agent (PID "
                + adoptedPid
                + "), killed "
                + killedCount
                + " duplicates for node: "
                + nodeFilter);
    }

    /** Force kill an SSH agent process */
    private boolean forceKillAgent(String pid, hudson.Launcher launcher, TaskListener listener) {
        try {
            List<String> killCmd = Arrays.asList(
                    "/bin/sh",
                    "-c",
                    String.format("kill -TERM %s 2>/dev/null || kill %s 2>/dev/null || true", pid, pid));

            launcher.launch().cmds(killCmd).quiet(true).start().joinWithTimeout(5, TimeUnit.SECONDS, listener);

            // Wait and verify the kill worked
            Thread.sleep(1000);

            List<String> checkCmd = Arrays.asList(
                    "/bin/sh",
                    "-c",
                    String.format(
                            "test ! -d /proc/%s || " + "[ \"$(awk '{print $3}' /proc/%s/stat 2>/dev/null)\" = \"Z\" ]",
                            pid, pid));

            int exitCode =
                    launcher.launch().cmds(checkCmd).quiet(true).start().joinWithTimeout(3, TimeUnit.SECONDS, listener);

            boolean killed = (exitCode == 0);
            LOGGER.info("Killed SSH agent PID " + pid + ": " + (killed ? "SUCCESS" : "FAILED"));
            return killed;

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to kill PID " + pid + ": " + e.getMessage());
            return false;
        }
    }

    /** Extract socket path from ssh-agent command line */
    private String extractSocketFromCmdline(String cmdline) {
        String[] parts = cmdline.split("\\s+");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("-a".equals(parts[i]) && i + 1 < parts.length) {
                return parts[i + 1];
            }
        }
        return null;
    }

    /** Adopt an SSH agent back into memory */
    private void adoptSshAgent(
            String nodeName, String socketPath, String pid, hudson.Launcher launcher, TaskListener listener) {
        try {
            SshAgent.adoptOrphanedAgent(nodeName, socketPath, pid);
            LOGGER.info("Adopted SSH agent: " + socketPath + " (PID: " + pid + ")");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to adopt SSH agent: " + e.getMessage(), e);
        }
    }
}
