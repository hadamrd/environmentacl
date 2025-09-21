package io.jenkins.plugins.pulsar.environment.parameters;

import hudson.Extension;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.StringParameterValue;
import hudson.model.TopLevelItem;
import io.jenkins.plugins.pulsar.environment.service.EnvironmentACLChecker;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;

public class EnvironmentChoiceParameterDefinition extends ParameterDefinition {
    private static final Logger LOGGER = Logger.getLogger(EnvironmentChoiceParameterDefinition.class.getName());

    @DataBoundConstructor
    public EnvironmentChoiceParameterDefinition(String name, String description) {
        super(name);
        setDescription(description);
    }

    private String getCurrentJobName() {
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

    public List<String> getChoices() {
        String jobFullName = getCurrentJobName();
        try {
            return EnvironmentACLChecker.getAccessibleEnvironments(jobFullName);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error loading environments for parameter ''{0}'': {1}", new Object[] {
                getName(), e.getMessage()
            });
            return List.of();
        }
    }

    @Override
    public ParameterValue createValue(StaplerRequest2 req, JSONObject jo) {
        String value = jo.optString("value", "");
        return new StringParameterValue(getName(), value, getDescription());
    }

    @Override
    public ParameterValue getDefaultParameterValue() {
        List<String> choices = getChoices();
        String defaultValue = choices.isEmpty() ? "" : choices.get(0);
        return new StringParameterValue(getName(), defaultValue, getDescription());
    }

    @Extension
    @Symbol("environmentChoice")
    public static class DescriptorImpl extends ParameterDefinition.ParameterDescriptor {
        @Override
        public String getDisplayName() {
            return "Environment Choice Parameter";
        }
    }
}
