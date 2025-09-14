package io.jenkins.plugins.environmentacl.parameter;

import hudson.model.AbstractBuild;
import hudson.model.ParameterValue;
import hudson.util.VariableResolver;
import org.kohsuke.stapler.DataBoundConstructor;

public class EnvironmentChoiceParameterValue extends ParameterValue {
    private final String value;

    @DataBoundConstructor
    public EnvironmentChoiceParameterValue(String name, String value, String description) {
        super(name, description);
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public VariableResolver<String> createVariableResolver(AbstractBuild<?, ?> build) {
        return name -> {
            if (getName().equals(name)) {
                return value;
            }
            return null;
        };
    }

    @Override
    public String toString() {
        return "(EnvironmentChoiceParameterValue) " + getName() + "='" + value + "'";
    }
}