package io.jenkins.plugins.pulsar.deployment;

import hudson.Extension;
import hudson.XmlFile;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;
import java.util.logging.Logger;

/** Main plugin initialization and configuration change handling */
public class DeploymentPlugin {

    private static final Logger LOGGER = Logger.getLogger(DeploymentPlugin.class.getName());

    @Initializer(after = InitMilestone.JOB_LOADED)
    public static void init() {
        LOGGER.info("Component Deployment Plugin initialized");
    }

    /** Listener that responds to configuration changes and updates jobs accordingly */
    @Extension
    public static class JobConfigurationListener extends SaveableListener {

        @Override
        public void onChange(Saveable o, XmlFile file) {
            if (o instanceof DeploymentGlobalConfiguration) {
                LOGGER.info("Component configuration changed, updating jobs...");
                try {
                    DeploymentJobManager.getInstance().updateAllJobs();
                } catch (Exception e) {
                    LOGGER.severe("Failed to update jobs after configuration change: " + e.getMessage());
                }
            }
        }
    }
}
