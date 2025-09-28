package io.jenkins.plugins.pulsar.container.service;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.slaves.ComputerListener;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

@Extension
public class NodeContainerDiscovery extends ComputerListener {
    private static final Logger LOGGER = Logger.getLogger(NodeContainerDiscovery.class.getName());

    /** Run container discovery when a node comes online */
    @Override
    public void onOnline(Computer computer, TaskListener listener) {
        // Schedule discovery to run after a delay to ensure channels are ready
        new Thread(() -> {
                    try {
                        // Wait a bit for channels to be fully established
                        Thread.sleep(3000);

                        String nodeName = computer.getName();
                        LOGGER.info("Starting container discovery for node: " + nodeName);

                        // Get launcher for this computer
                        hudson.Launcher launcher;
                        if (computer == Jenkins.get().toComputer()) {
                            // This is the master/controller node
                            launcher = new hudson.Launcher.LocalLauncher(listener);
                        } else if (computer.getChannel() != null) {
                            // This is an agent node with an active channel
                            launcher = new hudson.Launcher.RemoteLauncher(listener, computer.getChannel(), false);
                        } else {
                            LOGGER.warning(
                                    "No channel available for node: " + nodeName + ", skipping container discovery");
                            return;
                        }

                        // Run discovery for this node
                        discoverAndAdoptContainers(nodeName, launcher, listener);

                        LOGGER.info("Container discovery completed for node: " + nodeName);

                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Failed to discover containers for node: " + computer.getName(), e);
                    }
                })
                .start();
    }

    /** Discover managed containers and add them back to memory */
    private void discoverAndAdoptContainers(String nodeName, hudson.Launcher launcher, TaskListener listener) {
        try {
            // Handle empty node name (master/controller)
            String nodeFilter = nodeName != null && !nodeName.trim().isEmpty() ? nodeName : "master";

            // Find all running containers with our management label on this node
            List<String> findCmd = Arrays.asList(
                    "docker",
                    "ps",
                    "-q",
                    "--no-trunc",
                    "--filter",
                    "label=" + ContainerManager.PLUGIN_LABEL,
                    "--filter",
                    "label=" + ContainerManager.NODE_LABEL_PREFIX + nodeFilter,
                    "--filter",
                    "status=running");

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            int exitCode = launcher.launch()
                    .cmds(findCmd)
                    .stdout(output)
                    .stderr(output)
                    .quiet(true)
                    .start()
                    .joinWithTimeout(20, TimeUnit.SECONDS, listener);

            if (exitCode == 0) {
                String result = output.toString(StandardCharsets.UTF_8).trim();
                if (!result.isEmpty()) {
                    String[] containerIds = result.split("\n");
                    LOGGER.info("Found " + containerIds.length + " managed container(s) for node: " + nodeFilter);

                    for (String containerId : containerIds) {
                        if (containerId.trim().isEmpty()) continue;

                        // Get the image for this container using docker inspect
                        List<String> inspectCmd = Arrays.asList(
                                "docker",
                                "inspect",
                                containerId.trim(),
                                "--format",
                                "{{index .Config.Labels \"io.jenkins.sharedcontainer.image\"}}");

                        ByteArrayOutputStream inspectOutput = new ByteArrayOutputStream();
                        int inspectExitCode = launcher.launch()
                                .cmds(inspectCmd)
                                .stdout(inspectOutput)
                                .stderr(inspectOutput)
                                .quiet(true)
                                .start()
                                .joinWithTimeout(20, TimeUnit.SECONDS, listener);

                        if (inspectExitCode == 0) {
                            String imageWithPrefix = inspectOutput
                                    .toString(StandardCharsets.UTF_8)
                                    .trim();
                            if (!imageWithPrefix.isEmpty()) {
                                // Remove the label prefix to get clean image name
                                String image = imageWithPrefix.trim();
                                if (!image.isEmpty()) {
                                    try {
                                        ContainerManager.adoptContainer(nodeFilter, image, containerId.trim());
                                        LOGGER.fine("Adopted managed container: "
                                                + getShortId(containerId)
                                                + " (image: "
                                                + image
                                                + ")");

                                    } catch (Exception e) {
                                        LOGGER.log(Level.WARNING, "Failed to adopt container: " + e.getMessage(), e);
                                    }
                                }
                            }
                        } else {
                            LOGGER.log(Level.WARNING, "Failed to inspect container {0} (exit code: {1})", new Object[] {
                                getShortId(containerId), inspectExitCode
                            });
                        }
                    }
                } else {
                    LOGGER.log(Level.SEVERE, "No managed containers found for node: " + nodeFilter);
                }
            } else {
                LOGGER.log(Level.SEVERE, "Failed to query Docker for managed containers (exit code: " + exitCode + ")");
            }

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to discover managed containers", e);
        }
    }

    private String getShortId(String fullId) {
        return fullId != null && fullId.length() > 12 ? fullId.substring(0, 12) : fullId;
    }
}
