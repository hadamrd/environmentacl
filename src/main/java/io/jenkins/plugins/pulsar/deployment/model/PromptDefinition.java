package io.jenkins.plugins.pulsar.deployment.model;

import hudson.model.BooleanParameterDefinition;
import hudson.model.ChoiceParameterDefinition;
import hudson.model.ParameterDefinition;
import hudson.model.StringParameterDefinition;
import io.jenkins.plugins.pulsar.ansible.parameters.AnsibleProjectRefParameterDefinition;
import io.jenkins.plugins.pulsar.environment.parameters.EnvironmentChoiceParameterDefinition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class PromptDefinition {
    private final String name;
    private final String type;
    private String description;
    private List<JobParameter> properties = new ArrayList<>();

    @DataBoundConstructor
    public PromptDefinition(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public List<JobParameter> getProperties() {
        return properties;
    }

    @DataBoundSetter
    public void setDescription(String description) {
        this.description = description;
    }

    @DataBoundSetter
    public void setProperties(List<JobParameter> properties) {
        this.properties = properties != null ? properties : new ArrayList<>();
    }

    /** Convenience getter for common properties */
    public String getProperty(String key) {
        return properties.stream()
                .filter(param -> key.equals(param.getName()))
                .map(JobParameter::getValue)
                .findFirst()
                .orElse(null);
    }

    public List<String> getListProperty(String key) {
        String value = getProperty(key);
        if (value != null) {
            // Handle comma-separated values
            if (value.contains(",")) {
                return Arrays.asList(value.split(","));
            }
            // Single value
            return Arrays.asList(value);
        }
        return new ArrayList<>();
    }

    /** Convert to Jenkins ParameterDefinition using generic properties */
    public ParameterDefinition toParameterDefinition() {
        switch (type.toLowerCase()) {
            case "string" -> {
                return new StringParameterDefinition(name, getProperty("defaultValue"), description);
            }

            case "choice" -> {
                List<String> choices = getListProperty("choices");
                if (!choices.isEmpty()) {
                    return new ChoiceParameterDefinition(name, choices.toArray(String[]::new), description);
                }
            }

            case "boolean" -> {
                String defaultVal = getProperty("defaultValue");
                return new BooleanParameterDefinition(
                        name, Boolean.parseBoolean(defaultVal != null ? defaultVal : "false"), description);
            }

            case "environment" -> {
                return new EnvironmentChoiceParameterDefinition(name, description, getProperty("environmentGroup"));
            }

            case "ansibleprojectref" -> {
                return new AnsibleProjectRefParameterDefinition(name, description, getProperty("projectId"));
            }
            case "password" -> {
                return new hudson.model.PasswordParameterDefinition(
                        name,
                        hudson.util.Secret.fromString(""), // Empty default for security
                        description);
            }
        }

        // Default fallback
        return new StringParameterDefinition(name, getProperty("defaultValue"), description);
    }
}
