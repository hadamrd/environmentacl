package io.jenkins.plugins.pulsar.environment.parameters;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.StringParameterValue;
import io.jenkins.plugins.pulsar.environment.service.EnvironmentACLChecker;
import io.jenkins.plugins.pulsar.shared.RequestContextHelper;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest2;

public class EnvironmentChoiceParameterDefinition extends ParameterDefinition {
    private static final Logger LOGGER = Logger.getLogger(EnvironmentChoiceParameterDefinition.class.getName());

    private String environmentGroup;

    @DataBoundConstructor
    public EnvironmentChoiceParameterDefinition(String name, String description, String environmentGroup) {
        super(name);
        setDescription(description);
        this.environmentGroup = environmentGroup;
    }

    public EnvironmentChoiceParameterDefinition(String name, String description) {
        this(name, description, null);
    }

    public String getEnvironmentGroup() {
        return environmentGroup;
    }

    @DataBoundSetter
    public void setEnvironmentGroup(String environmentGroup) {
        this.environmentGroup = environmentGroup;
    }

    public List<String> getChoices() {
        String jobFullName = RequestContextHelper.getCurrentJobName();
        try {
            if (environmentGroup == null || environmentGroup.trim().isEmpty()) {
                // Default behavior - show all accessible environments
                LOGGER.info("Getting all accessible environments for job: " + jobFullName);
                return EnvironmentACLChecker.getAccessibleEnvironments(jobFullName);
            } else {
                // Filtered behavior - show only environments in the specified group
                LOGGER.info("Getting accessible environments for job: " + jobFullName + ", group: " + environmentGroup);
                return EnvironmentACLChecker.getAccessibleEnvironmentsByGroup(jobFullName, environmentGroup);
            }
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
