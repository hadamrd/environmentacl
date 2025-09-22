package io.jenkins.plugins.pulsar.ssh.service;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.pulsar.shared.LaunchHelper;
import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Singleton SSH Agent manager that handles SSH keys across multiple contexts */
public class SshAgent implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final String SSH_AGENTS_DIR = "/tmp/jenkins-ssh-agents";

    // Per-node singleton instances
    private static final Map<String, SshAgent> instances = new ConcurrentHashMap<>();
    private static final ReentrantLock globalLock = new ReentrantLock();

    private final String nodeName;
    private String agentPid;
    private String socketPath;
    private String expectedAgentOutput = "";

    // Track loaded keys with reference counting
    private final transient Map<String, Integer> loadedKeys = new ConcurrentHashMap<>();
    private final transient ReentrantLock instanceLock = new ReentrantLock();

    private SshAgent(String nodeName) {
        this.nodeName = nodeName;
    }

    /** Get singleton instance for the current node */
    public static SshAgent getInstance(String nodeName) {
        globalLock.lock();
        try {
            return instances.computeIfAbsent(nodeName, SshAgent::new);
        } finally {
            globalLock.unlock();
        }
    }

    public void start(Launcher launcher, TaskListener listener) throws Exception {
        instanceLock.lock();
        try {
            if (isRunning(launcher, listener)) {
                listener.getLogger().println("SSH agent already running: " + socketPath);
                return;
            }

            // Clear loaded keys tracking since we're starting fresh
            loadedKeys.clear();

            // Create consistent directory
            String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            this.socketPath = String.format("%s/agent-%s-%s.sock", SSH_AGENTS_DIR, nodeName, uuid);
            
            // Create directory and start agent
            List<String> startCmd = Arrays.asList(
                "/bin/sh", "-c",
                String.format("mkdir -p %s && chmod 700 %s && ssh-agent -s -a %s", 
                             SSH_AGENTS_DIR, SSH_AGENTS_DIR, socketPath)
            );

            String agentOutput = LaunchHelper.executeAndCapture(launcher, startCmd, 30, listener);
            
            Pattern pattern = Pattern.compile("SSH_AGENT_PID=(\\d+)");
            Matcher matcher = pattern.matcher(agentOutput);
            if (matcher.find()) {
                this.agentPid = matcher.group(1);
                listener.getLogger().println("SSH agent started with PID: " + agentPid);

                // Capture initial state (should be empty)
                updateExpectedState(launcher, listener);
            } else {
                throw new RuntimeException("Failed to extract PID from ssh-agent output: " + agentOutput);
            }

        } finally {
            instanceLock.unlock();
        }
    }

    /** Load SSH keys into the agent using stdin (no temporary files) */
    public void loadKeys(List<String> credentialIds, Run<?, ?> run, Launcher launcher, TaskListener listener)
            throws Exception {
        if (credentialIds == null || credentialIds.isEmpty()) {
            return;
        }

        instanceLock.lock();
        try {
            if (!isRunning(launcher, listener)) {
                throw new IllegalStateException("SSH agent not running");
            }

            // Find keys that need to be loaded
            List<String> keysToLoad = credentialIds.stream()
                    .filter(credId -> loadedKeys.getOrDefault(credId, 0) == 0)
                    .collect(Collectors.toList());

            if (keysToLoad.isEmpty()) {
                listener.getLogger().println("All SSH keys already loaded");
                // Still increment reference counts
                credentialIds.forEach(credId -> loadedKeys.merge(credId, 1, Integer::sum));
                return;
            }

            listener.getLogger().println("Loading SSH keys: " + keysToLoad);

            int keysAdded = 0;
            
            // Load each key directly via stdin - much more secure!
            for (String credentialId : keysToLoad) {
                SSHUserPrivateKey credential =
                        CredentialsProvider.findCredentialById(credentialId, SSHUserPrivateKey.class, run);

                if (credential == null) {
                    listener.getLogger().println("Warning: SSH credential not found: " + credentialId);
                    continue;
                }

                List<String> privateKeys = credential.getPrivateKeys();
                for (String privateKey : privateKeys) {
                    if (privateKey == null || privateKey.trim().isEmpty()) {
                        continue;
                    }

                    // Add key directly via stdin with 1 hour timeout
                    List<String> addCmd = Arrays.asList(
                            "/bin/sh", "-c",
                            String.format("SSH_AUTH_SOCK='%s' ssh-add -t 3600 -", socketPath)
                    );

                    ByteArrayInputStream keyInput = new ByteArrayInputStream(privateKey.getBytes());

                    int result = launcher.launch()
                            .cmds(addCmd)
                            .stdin(keyInput)
                            .quiet(true)
                            .stdout(listener.getLogger())
                            .stderr(listener.getLogger())
                            .start()
                            .joinWithTimeout(15, TimeUnit.SECONDS, listener);

                    if (result != 0) {
                        throw new RuntimeException("Failed to add SSH key for credential: " + credentialId);
                    }
                    
                    keysAdded++;
                }
            }

            if (keysAdded > 0) {
                // Update reference counts only if successful
                credentialIds.forEach(credId -> loadedKeys.merge(credId, 1, Integer::sum));
                listener.getLogger().println(String.format("Added %d SSH keys. Agent has %d unique credentials", 
                                                        keysAdded, loadedKeys.size()));

                // Update expected state after adding keys
                updateExpectedState(launcher, listener);
            } else {
                listener.getLogger().println("No keys were added");
            }

        } finally {
            instanceLock.unlock();
        }
    }

    /** Release keys (decrement reference count) */
    public void releaseKeys(List<String> credentialIds, TaskListener listener) {
        if (credentialIds == null || credentialIds.isEmpty()) {
            return;
        }

        instanceLock.lock();
        try {
            credentialIds.forEach(credId -> {
                Integer refCount = loadedKeys.get(credId);
                if (refCount != null && refCount > 1) {
                    loadedKeys.put(credId, refCount - 1);
                } else if (refCount != null) {
                    loadedKeys.remove(credId);
                }
            });

            listener.getLogger().println("Released SSH keys. Agent has " + loadedKeys.size() + " unique keys");
        } finally {
            instanceLock.unlock();
        }
    }

    /** Check if SSH agent is running */
    public boolean isRunning(Launcher launcher, TaskListener listener) {
        if (agentPid == null || socketPath == null) {
            return false;
        }

        try {
            List<String> checkCmd = Arrays.asList("test", "-d", "/proc/" + agentPid);
            int result = LaunchHelper.executeQuietly(launcher, checkCmd, listener.getLogger(), 5, listener);
            return result == 0;
        } catch (Exception e) {
            listener.getLogger().println("Error checking process: " + e.getMessage());
            return false;
        }
    }

    /** Stop SSH agent and cleanup */
    public void stop(Launcher launcher, TaskListener listener) {
        instanceLock.lock();
        try {
            if (agentPid == null) {
                return;
            }

            listener.getLogger().println("Stopping SSH agent: " + agentPid);

            // Kill agent
            List<String> killCmd = Arrays.asList("/bin/sh", "-c", String.format("kill %s", agentPid));
            int exitCode = launcher.launch()
                    .cmds(killCmd)
                    .stdout(listener.getLogger())
                    .stderr(listener.getLogger())
                    .quiet(true)
                    .start()
                    .joinWithTimeout(5, TimeUnit.SECONDS, listener);

            if (exitCode != 0) {
                listener.getLogger().println("[WARNING] Kill of the ssh agent failed !");
            } else {
                listener.getLogger().println("Agent stopped successfully");
            }

            // Only clean up the specific socket file if it still exists (defensive)
            // Don't delete the entire directory - could interfere with other processes
            if (socketPath != null) {
                List<String> cleanupSocketCmd = Arrays.asList("rm", "-f", socketPath);
                LaunchHelper.executeQuietlyDiscardOutput(launcher, cleanupSocketCmd, 10, listener);
            }

            agentPid = null;
            socketPath = null;
            loadedKeys.clear();
            expectedAgentOutput = "";

        } catch (Exception e) {
            listener.getLogger().println("Warning: Failed to stop SSH agent: " + e.getMessage());
        } finally {
            instanceLock.unlock();
        }
    }

    public void updateExpectedState(Launcher launcher, TaskListener listener) {
        try {
            List<String> listCmd = Arrays.asList(
                "/bin/sh", "-c", 
                String.format("SSH_AUTH_SOCK='%s' ssh-add -l 2>/dev/null || echo 'no keys'", socketPath)
            );
            
            this.expectedAgentOutput = LaunchHelper.executeAndCapture(launcher, listCmd, 5, listener);
        } catch (Exception e) {
            listener.getLogger().println("Failed to update expected state: " + e.getMessage());
        }
    }
    
    public boolean isSynced(Launcher launcher, TaskListener listener) {
        try {
            List<String> listCmd = Arrays.asList(
                "/bin/sh", "-c", 
                String.format("SSH_AUTH_SOCK='%s' ssh-add -l 2>/dev/null || echo 'no keys'", socketPath)
            );
            
            String currentOutput = LaunchHelper.executeAndCapture(launcher, listCmd, 5, listener);
            boolean synced = expectedAgentOutput.equals(currentOutput);
            
            if (!synced) {
                listener.getLogger().println("Expected: " + expectedAgentOutput);
                listener.getLogger().println("Actual: " + currentOutput);
            }
            
            return synced;
        } catch (Exception e) {
            return false;
        }
    }

    public static void adoptOrphanedAgent(String nodeName, String socketPath, String pid) {
        globalLock.lock();
        try {
            SshAgent agent = instances.get(nodeName);
            if (agent == null) {
                // Create new agent instance
                agent = new SshAgent(nodeName);
                instances.put(nodeName, agent);
            }
            
            // Set the discovered socket and PID
            agent.socketPath = socketPath;
            agent.agentPid = pid;
            // Clear loaded keys since we don't know what was loaded
            agent.loadedKeys.clear();
            
        } finally {
            globalLock.unlock();
        }
    }

    /** Cleanup all agents */
    public static void cleanupAll(Launcher launcher, TaskListener listener) {
        globalLock.lock();
        try {
            for (SshAgent agent : instances.values()) {
                agent.stop(launcher, listener);
            }
            instances.clear();
        } finally {
            globalLock.unlock();
        }
    }

    // Getters
    public String getSocketPath() {
        return socketPath;
    }

    public String getNodeName() {
        return nodeName;
    }

    public Set<String> getLoadedCredentialIds() {
        return new HashSet<>(loadedKeys.keySet());
    }

    public boolean hasKeys() {
        return !loadedKeys.isEmpty();
    }

    public void clearLoadedKeys() {
        loadedKeys.clear();
    }

    public boolean hasKey(String credId) {
        return loadedKeys.containsKey(credId);
    }

    /** Get the SSH agents directory for mounting */
    public static String getSshAgentsDir() {
        return SSH_AGENTS_DIR;
    }
}
