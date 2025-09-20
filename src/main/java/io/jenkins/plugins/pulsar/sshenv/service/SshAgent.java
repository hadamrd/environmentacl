package io.jenkins.plugins.pulsar.sshenv.service;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.pulsar.shared.LaunchHelper;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// the tmp folder in this one should be settable
// add of keys should be done with -t in case something went wrong and we dont kill the agent we
// want the keys to get cleaned
// isnt it better to maintain just one agent socket, but if one we might run into race
// conditions where some pipeline cleans it up at the end but another pipline is still in need of it
// => keeping a transient agent is better, or is it ?
// The reference on the keys is useless we just need to clean it up
// this agent will be called to instanciate one for the whole context of ansible step or ssh step
// for example
// will only be called by close of context of that step
// if that wrapper step has a context that can survive more calls this agent will survive and will
// be reused until the parent step cleanup is called

/** Singleton SSH Agent manager that handles SSH keys across multiple contexts */
public class SshAgent implements Serializable {
    private static final long serialVersionUID = 1L;

    // Per-node singleton instances
    private static final Map<String, SshAgent> instances = new ConcurrentHashMap<>();
    private static final ReentrantLock globalLock = new ReentrantLock();

    private final String nodeName;
    private String agentPid;
    private String socketPath;
    private String socketDir;

    // Track loaded keys with reference counting
    private final Map<String, Integer> loadedKeys = new ConcurrentHashMap<>();
    private final ReentrantLock instanceLock = new ReentrantLock();

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

    /** Start SSH agent if not already running */
    public void start(Launcher launcher, TaskListener listener) throws Exception {
        instanceLock.lock();
        try {
            if (isRunning(launcher, listener)) {
                listener.getLogger().println("SSH agent already running: " + socketPath);
                return;
            }

            // Generate unique paths
            String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            socketDir = "/tmp/ssh-agent-" + uuid;
            this.socketPath = socketDir + "/agent.sock";

            listener.getLogger().println("Starting SSH agent: " + socketPath);

            // Start SSH agent and capture output
            List<String> startCmd = Arrays.asList(
                    "/bin/sh",
                    "-c",
                    String.format(
                            "mkdir -p %s && chmod 700 %s && ssh-agent -s -a %s", 
                            socketDir, socketDir, socketPath));

            String agentOutput = LaunchHelper.executeAndCapture(launcher, startCmd, 30, listener);

            // Parse PID directly from output using regex
            Pattern pattern = Pattern.compile("SSH_AGENT_PID=(\\d+)");
            Matcher matcher = pattern.matcher(agentOutput);
            
            if (matcher.find()) {
                this.agentPid = matcher.group(1);
                listener.getLogger().println("SSH agent started with PID: " + agentPid);
            } else {
                throw new RuntimeException("Failed to extract PID from ssh-agent output: " + agentOutput);
            }

        } finally {
            instanceLock.unlock();
        }
    }

    /** Load SSH keys into the agent */
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

            // Collecter tous les fichiers de clés temporaires
            List<String> allKeyFiles = new ArrayList<>();
            String uuid = UUID.randomUUID().toString().substring(0, 8);

            try {
                // Créer fichiers temporaires pour toutes les clés
                for (String credentialId : keysToLoad) {
                    SSHUserPrivateKey credential = CredentialsProvider.findCredentialById(
                        credentialId, SSHUserPrivateKey.class, run);
                    
                    if (credential == null) {
                        listener.getLogger().println("Warning: SSH credential not found: " + credentialId);
                        continue;
                    }

                    List<String> privateKeys = credential.getPrivateKeys();
                    for (int i = 0; i < privateKeys.size(); i++) {
                        String privateKey = privateKeys.get(i);
                        if (privateKey == null || privateKey.trim().isEmpty()) {
                            continue;
                        }

                        String tempKeyFile = String.format("/tmp/ssh-key-%s-%d-%s.tmp", credentialId, i, uuid);
                        allKeyFiles.add(tempKeyFile);

                        // Créer fichier avec permissions sécurisées
                        String escapedKey = privateKey.replace("'", "'\"'\"'");
                        List<String> createCmd = Arrays.asList(
                            "/bin/sh", "-c", 
                            String.format("umask 077 && printf '%%s' '%s' > %s", escapedKey, tempKeyFile)
                        );
                        
                        LaunchHelper.executeQuietly(launcher, createCmd, listener.getLogger(), 10, listener);
                    }
                }

                // Ajouter toutes les clés en une fois avec timeout (1 heure)
                if (!allKeyFiles.isEmpty()) {
                    String keyFilesList = allKeyFiles.stream()
                        .map(f -> "'" + f + "'")
                        .collect(Collectors.joining(" "));
                        
                    List<String> addCmd = Arrays.asList(
                        "/bin/sh", "-c", 
                        String.format("SSH_AUTH_SOCK='%s' ssh-add -t 3600 %s", socketPath, keyFilesList)
                    );
                    
                    int result = LaunchHelper.executeQuietly(launcher, addCmd, listener.getLogger(), 30, listener);
                    if (result != 0) {
                        throw new RuntimeException("Failed to add SSH keys to agent");
                    }
                }

                // Update reference counts seulement si succès
                credentialIds.forEach(credId -> loadedKeys.merge(credId, 1, Integer::sum));
                
                listener.getLogger().println("SSH keys loaded. Agent has " + loadedKeys.size() + " unique keys");

            } finally {
                // Cleanup tous les fichiers temporaires
                for (String keyFile : allKeyFiles) {
                    List<String> cleanupCmd = Arrays.asList("rm", "-f", keyFile);
                    LaunchHelper.executeQuietlyDiscardOutput(launcher, cleanupCmd, 5, listener);
                }
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
            listener.getLogger().println("No agent pid or socket path found!");
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
            List<String> killCmd = Arrays.asList("kill", agentPid);
            LaunchHelper.executeQuietlyDiscardOutput(launcher, killCmd, 5, listener);

            // Cleanup socket directory
            if (socketDir != null) {
                List<String> cleanupCmd = Arrays.asList("rm", "-rf", socketDir);
                LaunchHelper.executeQuietlyDiscardOutput(launcher, cleanupCmd, 5, listener);
            }

            agentPid = null;
            socketPath = null;
            socketDir = null;
            loadedKeys.clear();

        } catch (Exception e) {
            listener.getLogger().println("Warning: Failed to stop SSH agent: " + e.getMessage());
        } finally {
            instanceLock.unlock();
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
}
