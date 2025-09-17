package io.jenkins.plugins.sharedcontainer.service;

import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class ContainerManager implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(ContainerManager.class.getName());

    // Static registry of active containers per node
    private static final Map<String, ContainerManager> activeContainers = new ConcurrentHashMap<>();

    private final String nodeName;
    private final String image;
    private final String containerId;
    private int referenceCount = 1;
    private volatile boolean isKilled = false;

    private ContainerManager(String nodeName, String image, String containerId) {
        this.nodeName = nodeName;
        this.image = image;
        this.containerId = containerId;
    }

    /** Get or create a shared container for the given image */
    public static ContainerManager getOrCreate(String nodeName, String image, Launcher launcher, TaskListener listener)
            throws IOException, InterruptedException {

        String containerKey = nodeName + ":" + image;

        synchronized (ContainerManager.class) {
            // Check if container already exists and is running
            ContainerManager existing = activeContainers.get(containerKey);
            if (existing != null && !existing.isKilled && existing.isRunning(launcher, listener)) {
                existing.referenceCount++;
                listener.getLogger().println("Reusing existing container: " + existing.containerId);
                return existing;
            }

            listener.getLogger().println("Creating new shared container for image: " + image);

            // Create new container
            String containerId = createContainer(image, launcher, listener);

            ContainerManager manager = new ContainerManager(nodeName, image, containerId);
            activeContainers.put(containerKey, manager);

            listener.getLogger().println("Created shared container: " + containerId);
            return manager;
        }
    }

    /** Create a new Docker container */
    private static String createContainer(String image, Launcher launcher, TaskListener listener)
            throws IOException, InterruptedException {

        // Build docker run command
        List<String> dockerCmd = new ArrayList<>();
        dockerCmd.add("docker");
        dockerCmd.add("run");
        dockerCmd.add("-d"); // Detached mode
        dockerCmd.add("-v");
        dockerCmd.add("/tmp:/tmp");
        dockerCmd.add(image);
        dockerCmd.add("sleep");
        dockerCmd.add("infinity");

        // Execute docker run and capture container ID
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        StreamTaskListener outputListener = new StreamTaskListener(outputStream, StandardCharsets.UTF_8);

        int exitCode = launcher.launch()
                .cmds(dockerCmd)
                .stdout(outputListener)
                .start()
                .joinWithTimeout(60, java.util.concurrent.TimeUnit.SECONDS, listener);

        if (exitCode != 0) {
            throw new IOException("Failed to create container for image: " + image + " (exit code: " + exitCode + ")");
        }

        String containerId = outputStream.toString(StandardCharsets.UTF_8).trim();
        if (containerId.isEmpty()) {
            throw new IOException("Failed to create container for image: " + image);
        }

        // Verify container is running
        List<String> checkCmd = List.of("docker", "inspect", "-f", "{{.State.Running}}", containerId);
        ByteArrayOutputStream checkStream = new ByteArrayOutputStream();
        StreamTaskListener checkListener = new StreamTaskListener(checkStream, StandardCharsets.UTF_8);

        exitCode = launcher.launch()
                .cmds(checkCmd)
                .stdout(checkListener)
                .start()
                .joinWithTimeout(10, java.util.concurrent.TimeUnit.SECONDS, listener);

        if (exitCode != 0
                || !"true".equals(checkStream.toString(StandardCharsets.UTF_8).trim())) {
            throw new IOException("Container failed to start properly: " + containerId);
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

        if (isKilled) {
            listener.getLogger().println("Cannot execute: container " + containerId + " has been killed");
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

        dockerCmd.add(containerId);
        dockerCmd.add("/bin/sh");
        dockerCmd.add("-c");
        dockerCmd.add(command);

        return launcher.launch()
                .cmds(dockerCmd)
                .stdout(listener.getLogger())
                .stderr(listener.getLogger())
                .start()
                .join();
    }

    /** Release reference to this container */
    public void release(boolean keepContainer, Launcher launcher, TaskListener listener) {
        synchronized (ContainerManager.class) {
            referenceCount--;
            listener.getLogger().println("Container " + containerId + " reference count: " + referenceCount);

            if (referenceCount <= 0 && !keepContainer) {
                kill(launcher, listener);
                activeContainers.remove(nodeName + ":" + image);
            }
        }
    }

    /** Kill this container */
    private void kill(Launcher launcher, TaskListener listener) {
        if (isKilled || containerId == null) {
            return;
        }

        try {
            // Force remove the container
            launcher.launch()
                    .cmds("docker", "rm", "-f", containerId)
                    .stdout(listener.getLogger())
                    .stderr(listener.getLogger())
                    .start()
                    .join();

            listener.getLogger().println("Killed container: " + containerId);
        } catch (Exception e) {
            LOGGER.warning("Failed to kill container " + containerId + ": " + e.getMessage());
        } finally {
            isKilled = true;
        }
    }

    /** Check if container is still running */
    private boolean isRunning(Launcher launcher, TaskListener listener) {
        if (isKilled || containerId == null) {
            return false;
        }

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            StreamTaskListener outputListener = new StreamTaskListener(outputStream, StandardCharsets.UTF_8);

            int exitCode = launcher.launch()
                    .cmds("docker", "inspect", "-f", "{{.State.Running}}", containerId)
                    .stdout(outputListener)
                    .start()
                    .joinWithTimeout(5, java.util.concurrent.TimeUnit.SECONDS, listener);

            return exitCode == 0
                    && "true"
                            .equals(outputStream
                                    .toString(StandardCharsets.UTF_8)
                                    .trim());
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
                    LOGGER.warning("Failed to cleanup container " + container.containerId + ": " + e.getMessage());
                }
            }

            activeContainers.clear();
        }
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
