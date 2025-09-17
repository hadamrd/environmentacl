package io.jenkins.plugins.sharedcontainer.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.Launcher;
import hudson.model.TaskListener;

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

    /**
     * Get or create a shared container for the given image
     */
    public static ContainerManager getOrCreate(String nodeName, String image, 
                                              Launcher launcher, TaskListener listener) 
                                              throws IOException, InterruptedException {
        
        String containerKey = nodeName + ":" + image;
        
        synchronized (ContainerManager.class) {
            // Check if container already exists and is running
            ContainerManager existing = activeContainers.get(containerKey);
            if (existing != null && !existing.isKilled && existing.isRunning(launcher, listener)) {
                existing.referenceCount++;
                listener.getLogger().println("Reusing existing shared container: " + existing.getShortId());
                return existing;
            }

            listener.getLogger().println("Creating new shared container for image: " + image);
            
            // Create new container
            String containerId = createContainer(image, launcher, listener);
            
            ContainerManager manager = new ContainerManager(nodeName, image, containerId);
            activeContainers.put(containerKey, manager);
            
            listener.getLogger().println("Created shared container: " + manager.getShortId());
            return manager;
        }
    }

    /**
     * Create a new Docker container - with quiet execution
     */
    private static String createContainer(String image, Launcher launcher, TaskListener listener) 
                                        throws IOException, InterruptedException {
        
        // Build docker run command
        List<String> dockerCmd = new ArrayList<>();
        dockerCmd.add("docker");
        dockerCmd.add("run");
        dockerCmd.add("-d");  // Detached mode
        dockerCmd.add("-v");
        dockerCmd.add("/tmp:/tmp");
        dockerCmd.add(image);
        dockerCmd.add("sleep");
        dockerCmd.add("infinity");

        // Execute docker run and capture container ID - QUIETLY
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        int exitCode = launcher.launch()
                .cmds(dockerCmd)
                .stdout(outputStream)
                .stderr(outputStream)  // Capture stderr too
                .quiet(true)  // ✅ Don't print the command to build log
                .start()
                .joinWithTimeout(60, java.util.concurrent.TimeUnit.SECONDS, listener);
        
        if (exitCode != 0) {
            // Only show error output if something went wrong
            String error = outputStream.toString(StandardCharsets.UTF_8);
            listener.getLogger().println("Failed to create container for image: " + image + " (exit code: " + exitCode + ")");
            listener.getLogger().println("Docker output: " + error);
            throw new IOException("Failed to create container for image: " + image);
        }
        
        String containerId = outputStream.toString(StandardCharsets.UTF_8).trim();
        if (containerId.isEmpty()) {
            throw new IOException("Failed to create container for image: " + image);
        }

        // Verify container is running - QUIETLY
        List<String> checkCmd = List.of("docker", "inspect", "-f", "{{.State.Running}}", containerId);
        ByteArrayOutputStream checkStream = new ByteArrayOutputStream();
        
        exitCode = launcher.launch()
                .cmds(checkCmd)
                .stdout(checkStream)
                .stderr(checkStream)
                .quiet(true)  // ✅ Don't print the command to build log
                .start()
                .joinWithTimeout(10, java.util.concurrent.TimeUnit.SECONDS, listener);

        if (exitCode != 0 || !"true".equals(checkStream.toString(StandardCharsets.UTF_8).trim())) {
            throw new IOException("Container failed to start properly: " + getShortId(containerId));
        }

        return containerId;
    }

    /**
     * Execute a command inside this container
     */
    public int execute(String command, Launcher launcher, TaskListener listener) 
                      throws IOException, InterruptedException {
        return execute(command, null, launcher, listener);
    }

    /**
     * Execute a command inside this container with custom user - with cleaner logging
     */
    public int execute(String command, String user, Launcher launcher, TaskListener listener) 
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
        
        dockerCmd.add(containerId);
        dockerCmd.add("/bin/sh");
        dockerCmd.add("-c");
        dockerCmd.add(command);

        // Show what we're executing, but cleaner
        listener.getLogger().println("Running: " + command);
        
        return launcher.launch()
                .cmds(dockerCmd)
                .stdout(listener.getLogger())
                .stderr(listener.getLogger())
                .quiet(true)  // ✅ Don't print the docker exec command itself
                .start()
                .join();
    }

    /**
     * Release reference to this container
     */
    public void release(boolean keepContainer, Launcher launcher, TaskListener listener) {
        synchronized (ContainerManager.class) {
            referenceCount--;
            
            if (referenceCount <= 0 && !keepContainer) {
                listener.getLogger().println("Removing shared container: " + getShortId());
                kill(launcher, listener);
                activeContainers.remove(nodeName + ":" + image);
            }
        }
    }

    /**
     * Kill this container - QUIETLY
     */
    private void kill(Launcher launcher, TaskListener listener) {
        if (isKilled || containerId == null) {
            return;
        }

        try {
            // Force remove the container - QUIETLY
            ByteArrayOutputStream devNull = new ByteArrayOutputStream();
            launcher.launch()
                    .cmds("docker", "rm", "-f", containerId)
                    .stdout(devNull)  // ✅ Discard output
                    .stderr(devNull)  // ✅ Discard errors too
                    .quiet(true)  // ✅ Don't print the command
                    .start()
                    .join();
                    
            listener.getLogger().println("Removed container: " + getShortId());
        } catch (Exception e) {
            LOGGER.warning("Failed to kill container " + getShortId() + ": " + e.getMessage());
            listener.getLogger().println("Warning: Failed to remove container " + getShortId());
        } finally {
            isKilled = true;
        }
    }

    /**
     * Check if container is still running - QUIETLY
     */
    private boolean isRunning(Launcher launcher, TaskListener listener) {
        if (isKilled || containerId == null) {
            return false;
        }

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            
            int exitCode = launcher.launch()
                    .cmds("docker", "inspect", "-f", "{{.State.Running}}", containerId)
                    .stdout(outputStream)
                    .stderr(outputStream)
                    .quiet(true)  // ✅ Don't print the command
                    .start()
                    .joinWithTimeout(5, java.util.concurrent.TimeUnit.SECONDS, listener);
                    
            return exitCode == 0 && "true".equals(outputStream.toString(StandardCharsets.UTF_8).trim());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Clean up all containers - with cleaner output
     */
    public static void cleanupAll(Launcher launcher, TaskListener listener) {
        synchronized (ContainerManager.class) {
            List<ContainerManager> containers = new ArrayList<>(activeContainers.values());
            
            if (containers.isEmpty()) {
                listener.getLogger().println("No shared containers to clean up");
                return;
            }
            
            for (ContainerManager container : containers) {
                try {
                    container.kill(launcher, listener);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to cleanup container {0}: {1}", new Object[]{container.getShortId(), e.getMessage()});
                }
            }
            
            activeContainers.clear();
        }
    }

    /**
     * Get short container ID for cleaner logging
     */
    private String getShortId() {
        return getShortId(containerId);
    }
    
    private static String getShortId(String fullId) {
        return fullId != null && fullId.length() > 12 ? fullId.substring(0, 12) : fullId;
    }

    // Getters
    public String getImage() { return image; }
    public String getContainerId() { return containerId; }
    public String getNodeName() { return nodeName; }
    public int getReferenceCount() { return referenceCount; }
    public boolean isKilled() { return isKilled; }
}