package io.jenkins.plugins.pulsar.sharedcontainer.service;

import hudson.Launcher;
import hudson.model.TaskListener;
import io.jenkins.plugins.pulsar.shared.LaunchHelper;
import io.jenkins.plugins.pulsar.sharedcontainer.steps.SharedContainerStep;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ContainerManager implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(ContainerManager.class.getName());
    public static final String PLUGIN_LABEL = "io.jenkins.sharedcontainer.managed=true";
    public static final String IMAGE_LABEL_PREFIX = "io.jenkins.sharedcontainer.image=";
    public static final String NODE_LABEL_PREFIX = "io.jenkins.sharedcontainer.node=";
    public static final String CREATED_LABEL_PREFIX = "io.jenkins.sharedcontainer.created=";

    // Static registry of active containers per node
    private static final Map<String, ContainerManager> activeContainers = new ConcurrentHashMap<>();

    private final String nodeName;
    private final String image;
    private final String containerId;
    private int referenceCount = 1;
    private volatile boolean isKilled = false;

    // Environment variables for this container instance
    private final Map<String, String> env = new ConcurrentHashMap<>();

    private ContainerManager(String nodeName, String image, String containerId) {
        this.nodeName = nodeName;
        this.image = image;
        this.containerId = containerId;
    }

    /** Get or create a shared container for the given image */
    public static ContainerManager getOrCreate(
            String nodeName, String image, SharedContainerStep step, Launcher launcher, TaskListener listener)
            throws IOException, InterruptedException {

        String containerKey = nodeName + ":" + image;

        synchronized (ContainerManager.class) {
            ContainerManager existing = activeContainers.get(containerKey);
            if (existing != null && !existing.isKilled && existing.isRunning(launcher, listener)) {
                existing.referenceCount++;
                listener.getLogger().println("Reusing active container: " + existing.getShortId());
                return existing;
            }

            // Create new container
            listener.getLogger().println("Creating new shared container for image: " + image);
            String containerId = createContainer(nodeName, image, step, launcher, listener);

            ContainerManager manager = new ContainerManager(nodeName, image, containerId);
            activeContainers.put(containerKey, manager);

            listener.getLogger().println("Created shared container: " + manager.getShortId());
            return manager;
        }
    }

    /** Create a new Docker container */
    private static String createContainer(
            String nodeName, String image, SharedContainerStep step, Launcher launcher, TaskListener listener)
            throws IOException, InterruptedException {
        List<String> dockerCmd = new ArrayList<>(step.buildDockerRunArgs());

        dockerCmd.add("--label");
        dockerCmd.add(PLUGIN_LABEL);
        dockerCmd.add("--label");
        dockerCmd.add(IMAGE_LABEL_PREFIX + image);
        dockerCmd.add("--label");
        dockerCmd.add(NODE_LABEL_PREFIX + nodeName);
        dockerCmd.add("--label");
        dockerCmd.add(CREATED_LABEL_PREFIX + System.currentTimeMillis());

        // Build docker run command with tracking labels
        dockerCmd.add(image);
        dockerCmd.add("sleep");
        dockerCmd.add(String.valueOf(step.getTimeoutHours() * 3600));

        listener.getLogger().println("Docker command: " + String.join(" ", dockerCmd));

        String containerId = LaunchHelper.executeAndCapture(launcher, dockerCmd, 60, listener);
        if (containerId == null || containerId.isEmpty()) {
            throw new IOException("Failed to create container for image: " + image);
        }

        // Verify container is running
        List<String> statusCmd = List.of(
                "docker", "inspect", "-f", "{{.State.Running}} {{.State.Status}} {{.State.ExitCode}}", containerId);
        String statusInfo = LaunchHelper.executeAndCapture(launcher, statusCmd, 10, listener);

        if (statusInfo == null || !statusInfo.startsWith("true")) {
            // Container failed - cleanup and throw
            List<String> cleanupCmd = List.of("docker", "rm", "-f", containerId);
            LaunchHelper.executeQuietlyDiscardOutput(launcher, cleanupCmd, 10, listener);
            throw new IOException("Container failed to start. Status: " + statusInfo);
        }

        return containerId;
    }

    /** Execute a command inside this container */
    public int execute(String command, Launcher launcher, TaskListener listener)
            throws IOException, InterruptedException {
        return execute(command, null, launcher, listener);
    }

    /** Execute a command inside this container with custom user */
    public int execute(String command, String user, Launcher launcher, TaskListener listener)
            throws IOException, InterruptedException {
        return execute(command, user, null, launcher, listener);
    }

    /** Execute a command inside this container with custom user and environment variables */
    public int execute(
            String command, String user, Map<String, String> additionalEnv, Launcher launcher, TaskListener listener)
            throws IOException, InterruptedException {

        if (isKilled) {
            listener.getLogger().println("Cannot execute: container " + getShortId() + " has been killed");
            return -1;
        }

        // Build docker exec command
        List<String> dockerCmd = new ArrayList<>();
        dockerCmd.add("docker");
        dockerCmd.add("exec");
        dockerCmd.add("-i");

        // Add user if specified
        if (user != null && !user.trim().isEmpty()) {
            dockerCmd.add("-u");
            dockerCmd.add(user);
        }

        // Combine environment variables (instance env + additional env)
        Map<String, String> allEnv = new LinkedHashMap<>(this.env);
        if (additionalEnv != null) {
            allEnv.putAll(additionalEnv);
        }

        // Add environment variables to docker exec
        for (Map.Entry<String, String> envVar : allEnv.entrySet()) {
            dockerCmd.add("-e");
            dockerCmd.add(envVar.getKey() + "=" + envVar.getValue());
        }

        dockerCmd.add(containerId);
        dockerCmd.add("/bin/sh");
        dockerCmd.add("-c");
        dockerCmd.add(command);

        listener.getLogger().println("Running: " + command);
        if (!allEnv.isEmpty()) {
            listener.getLogger().println("Environment: " + envVarsToString(allEnv));
        }

        return LaunchHelper.executeQuietly(launcher, dockerCmd, listener.getLogger(), 30, listener);
    }

    /** Execute with SSH keys available - similar to Groovy withSshKeys */
    public int executeWithSshKeys(String command, List<String> credentialIds, Launcher launcher, TaskListener listener)
            throws Exception {
        // This would integrate with SshAgent
        // For now, placeholder that shows the pattern
        Map<String, String> sshEnv = new HashMap<>();
        // sshEnv.put("SSH_AUTH_SOCK", sshAgent.getSocketPath());

        return execute(command, "root", sshEnv, launcher, listener);
    }

    /** Set environment variable for this container instance */
    public void setEnv(String key, String value) {
        if (key != null && value != null) {
            env.put(key, value);
        }
    }

    /** Set multiple environment variables */
    public void setEnv(Map<String, String> envVars) {
        if (envVars != null) {
            env.putAll(envVars);
        }
    }

    /** Get environment variable */
    public String getEnv(String key) {
        return env.get(key);
    }

    /** Get all environment variables */
    public Map<String, String> getAllEnv() {
        return new HashMap<>(env);
    }

    /** Clear environment variable */
    public void clearEnv(String key) {
        env.remove(key);
    }

    /** Clear all environment variables */
    public void clearAllEnv() {
        env.clear();
    }

    /** Release reference to this container */
    public void release(boolean cleanup, Launcher launcher, TaskListener listener) {
        synchronized (ContainerManager.class) {
            referenceCount--;

            if (referenceCount <= 0 && cleanup) {
                listener.getLogger().println("Removing shared container: " + getShortId());
                kill(launcher, listener);
                activeContainers.remove(nodeName + ":" + image);
            } else if (referenceCount <= 0) {
                listener.getLogger().println("Keeping container alive: " + getShortId() + " (cleanup=false)");
            }
        }
    }

    /** Kill this container */
    private void kill(Launcher launcher, TaskListener listener) {
        if (isKilled || containerId == null) {
            return;
        }

        try {
            List<String> removeCmd = List.of("docker", "rm", "-f", containerId);
            LaunchHelper.executeQuietlyDiscardOutput(launcher, removeCmd, 30, listener);
            listener.getLogger().println("Removed container: " + getShortId());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to kill container {0}: {1}", new Object[] {getShortId(), e.getMessage()});
            listener.getLogger().println("Warning: Failed to remove container " + getShortId());
        } finally {
            isKilled = true;
            env.clear(); // Clear environment variables
        }
    }

    /** Check if container is still running */
    private boolean isRunning(Launcher launcher, TaskListener listener) {
        if (isKilled || containerId == null) {
            return false;
        }

        try {
            List<String> checkCmd = List.of("docker", "inspect", "-f", "{{.State.Running}}", containerId);
            String result = LaunchHelper.executeAndCapture(launcher, checkCmd, 5, listener);
            return "true".equals(result);
        } catch (Exception e) {
            return false;
        }
    }

    /** Clean up all containers */
    public static void cleanupAll(Launcher launcher, TaskListener listener) {
        synchronized (ContainerManager.class) {
            List<ContainerManager> containers = new ArrayList<>(activeContainers.values());

            for (ContainerManager container : containers) {
                try {
                    container.kill(launcher, listener);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to cleanup container {0}: {1}", new Object[] {
                        container.getShortId(), e.getMessage()
                    });
                }
            }
            activeContainers.clear();
        }
    }

    // Helper methods

    /** Convert environment variables to readable string */
    private String envVarsToString(Map<String, String> envVars) {
        if (envVars.isEmpty()) {
            return "none";
        }

        List<String> envList = new ArrayList<>();
        for (Map.Entry<String, String> entry : envVars.entrySet()) {
            // Don't log sensitive values in full
            String value = entry.getValue();
            if (entry.getKey().toLowerCase().contains("password")
                    || entry.getKey().toLowerCase().contains("secret")
                    || entry.getKey().toLowerCase().contains("token")) {
                value = "***";
            } else if (value.length() > 50) {
                value = value.substring(0, 47) + "...";
            }
            envList.add(entry.getKey() + "=" + value);
        }
        return String.join(", ", envList);
    }

    /** Get short container ID for cleaner logging */
    private String getShortId() {
        return getShortId(containerId);
    }

    private static String getShortId(String fullId) {
        return fullId != null && fullId.length() > 12 ? fullId.substring(0, 12) : fullId;
    }

    // Getters
    public String getImage() {
        return image;
    }

    public String getContainerId() {
        return containerId;
    }

    public String getNodeName() {
        return nodeName;
    }

    public int getReferenceCount() {
        return referenceCount;
    }

    public boolean isKilled() {
        return isKilled;
    }
}
