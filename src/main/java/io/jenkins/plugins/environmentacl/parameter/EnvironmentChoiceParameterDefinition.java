package io.jenkins.plugins.environmentacl.parameter;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.StringParameterValue;
import io.jenkins.plugins.environmentacl.service.EnvironmentACLChecker;
import java.util.List;
import java.util.logging.Logger;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

public class EnvironmentChoiceParameterDefinition extends ParameterDefinition {
    private static final Logger LOGGER = Logger.getLogger(EnvironmentChoiceParameterDefinition.class.getName());

    @DataBoundConstructor
    public EnvironmentChoiceParameterDefinition(String name, String description) {
        super(name);
        setDescription(description);
    }

    public List<String> getChoices() {
        try {
            String jobName = getCurrentJobFullName();
            List<String> accessibleEnvs = EnvironmentACLChecker.getAccessibleEnvironments(jobName);

            // Secure logging - only to Jenkins system log
            LOGGER.fine("Environment parameter '"
                    + getName()
                    + "' for job '"
                    + jobName
                    + "' returned "
                    + accessibleEnvs.size()
                    + " accessible environments");

            // Return actual choices only - no error messages mixed in
            return accessibleEnvs;

        } catch (Exception e) {
            LOGGER.warning("Error loading environments for parameter '" + getName() + "': " + e.getMessage());
            // Return empty list on error - let UI handle it
            return List.of();
        }
    }

    private String getCurrentJobFullName() {
        try {
            StaplerRequest req = org.kohsuke.stapler.Stapler.getCurrentRequest();
            if (req != null && req.getRequestURI().contains("/job/")) {
                return req.getRequestURI().split("/job/")[1].split("/")[0];
            }
        } catch (Exception e) {
            LOGGER.fine("Could not determine job name from request: " + e.getMessage());
        }
        return "*";
    }

    @Override
    public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
        String value = jo.optString("value", "");
        // No need to filter out error messages since we don't add them to choices
        return new StringParameterValue(getName(), value, getDescription());
    }

    @Override
    public ParameterValue createValue(StaplerRequest req) {
        String[] values = req.getParameterValues(getName());
        String value = (values != null && values.length > 0) ? values[0] : "";
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
