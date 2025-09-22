package io.jenkins.plugins.pulsar.shared;

import hudson.model.Job;
import hudson.model.TopLevelItem;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;

public class RequestContextHelper {
    private static final Logger LOGGER = Logger.getLogger(RequestContextHelper.class.getName());

    public static String getCurrentJobName() {
        try {
            StaplerRequest2 req = Stapler.getCurrentRequest2();
            if (req != null) {
                // Try to find a Job ancestor in the request
                Object ancestor = req.findAncestorObject(Job.class);
                if (ancestor instanceof Job) {
                    Job<?, ?> job = (Job<?, ?>) ancestor;
                    return job.getFullName();
                }

                // Try to find TopLevelItem
                ancestor = req.findAncestorObject(TopLevelItem.class);
                if (ancestor instanceof Job) {
                    Job<?, ?> job = (Job<?, ?>) ancestor;
                    return job.getFullName();
                }
            }
            return "*";
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting current job name: " + e.getMessage(), e);
            return "*";
        }
    }
}
