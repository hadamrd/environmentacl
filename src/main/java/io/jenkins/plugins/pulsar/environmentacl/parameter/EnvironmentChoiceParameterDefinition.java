package io.jenkins.plugins.pulsar.environmentacl.parameter;

import hudson.Extension;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.StringParameterValue;
import io.jenkins.plugins.pulsar.environmentacl.service.EnvironmentACLChecker;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


import net.sf.json.JSONObject;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest2;

public class EnvironmentChoiceParameterDefinition extends ParameterDefinition {
    private static final Logger LOGGER = Logger.getLogger(EnvironmentChoiceParameterDefinition.class.getName());

    @DataBoundConstructor
    public EnvironmentChoiceParameterDefinition(String name, String description) {
        super(name);
        setDescription(description);
    }

    public List<String> getChoices(String jobFullName) {
        try {
            List<String> accessibleEnvs = EnvironmentACLChecker.getAccessibleEnvironments(jobFullName);

            // Secure logging - only to Jenkins system log
            LOGGER.log(Level.FINE, "Environment parameter ''{0}'' for job ''{1}'' returned {2} accessible environments", new Object[]{getName(), jobFullName, accessibleEnvs.size()});

            // Return actual choices only - no error messages mixed in
            return accessibleEnvs;

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error loading environments for parameter ''{0}'': {1}", new Object[]{getName(), e.getMessage()});
            // Return empty list on error - let UI handle it
            return List.of();
        }
    }

    @Override
    public ParameterValue createValue(StaplerRequest2 req, JSONObject jo) {
        String value = jo.optString("value", "");
        // No need to filter out error messages since we don't add them to choices
        return new StringParameterValue(getName(), value, getDescription());
    }

    @Override
    public ParameterValue getDefaultParameterValue() {
        // FIXME: Find a way to get full job name here!
        String currJobFullName = "*";
        List<String> choices = getChoices(currJobFullName);
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
