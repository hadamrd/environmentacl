package io.jenkins.plugins.pulsar.deployment.model;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import java.io.Serializable;
import org.kohsuke.stapler.DataBoundConstructor;

public class JobParameter implements Describable<JobParameter>, Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    private final String value;

    @DataBoundConstructor
    public JobParameter(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    @Override
    public Descriptor<JobParameter> getDescriptor() {
        return new DescriptorImpl();
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<JobParameter> {
        @Override
        public String getDisplayName() {
            return "Component Parameter";
        }
    }
}
