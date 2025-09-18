package io.jenkins.plugins.sharedcontainer.service;

import hudson.Launcher;
import hudson.model.TaskListener;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ContainerCleaner {
    private static final Logger LOGGER = Logger.getLogger(ContainerCleaner.class.getName());

    /** Look for orphaned containers that match the given node and image */
    public static String findManagedContainer(String nodeName, String image, Launcher launcher, TaskListener listener) {
        try {
            // Find containers with our labels that match node+image
            List<String> findCmd = Arrays.asList(
                    "docker",
                    "ps",
                    "-q",
                    "--no-trunc",
                    "--filter",
                    "label=" + ContainerManager.PLUGIN_LABEL,
                    "--filter",
                    "label=" + ContainerManager.IMAGE_LABEL_PREFIX + image,
                    "--filter",
                    "label=" + ContainerManager.NODE_LABEL_PREFIX + nodeName,
                    "--filter",
                    "status=running");

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            int exitCode = LaunchHelper.executeQuietly(launcher, findCmd, output, 10, listener);

            if (exitCode == 0) {
                String result = output.toString(StandardCharsets.UTF_8).trim();
                if (!result.isEmpty()) {
                    String containerId = result.split("\n")[0]; // Take first match
                    listener.getLogger().println("Found orphaned container: " + getShortId(containerId));
                    return containerId;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to discover orphaned containers", e);
        }
        return null;
    }

    /** Clean up ALL orphaned containers managed by this plugin */
    public static void cleanupAllManagedContainers(Launcher launcher, TaskListener listener) {
        try {
            // Find all containers with our management label
            List<String> findCmd = Arrays.asList(
                    "docker", "ps", "-aq", "--no-trunc", "--filter", "label=" + ContainerManager.PLUGIN_LABEL);

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            int exitCode = LaunchHelper.executeQuietly(launcher, findCmd, output, 30, listener);

            if (exitCode == 0) {
                String result = output.toString(StandardCharsets.UTF_8).trim();
                if (!result.isEmpty()) {
                    String[] containerIds = result.split("\n");
                    listener.getLogger().println("Found " + containerIds.length + " orphaned container(s) to clean up");

                    for (String containerId : containerIds) {
                        if (containerId.trim().isEmpty()) continue;

                        try {
                            List<String> removeCmd = Arrays.asList("docker", "rm", "-f", containerId.trim());
                            LaunchHelper.executeQuietlyDiscardOutput(launcher, removeCmd, 10, listener);
                            listener.getLogger().println("Removed orphaned container: " + getShortId(containerId));
                        } catch (Exception e) {
                            LOGGER.log(Level.WARNING, "Failed to remove orphaned container {0}: {1}", new Object[] {
                                getShortId(containerId), e.getMessage()
                            });
                        }
                    }
                } else {
                    listener.getLogger().println("No orphaned containers found");
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to discover orphaned containers", e);
            listener.getLogger().println("Warning: Could not check for orphaned containers");
        }
    }

    private static String getShortId(String fullId) {
        return fullId != null && fullId.length() > 12 ? fullId.substring(0, 12) : fullId;
    }
}
