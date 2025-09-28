package io.jenkins.plugins.pulsar.deployment.model;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.kohsuke.stapler.DataBoundConstructor;

public class DeploymentJob implements Describable<DeploymentJob>, Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String name;
    private final String category;
    private final String templateName;
    private final List<JobParameter> params;

    @DataBoundConstructor
    public DeploymentJob(String id, String name, String category, String templateName, List<JobParameter> params) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.templateName = templateName;
        this.params = params != null ? params : new ArrayList<>();
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public String getTemplateName() {
        return templateName;
    }

    public List<JobParameter> getParams() {
        return params;
    }

    // Utility method to get params as Map for easier access in code
    public Map<String, String> getParamsAsMap() {
        Map<String, String> result = new HashMap<>();
        for (JobParameter param : params) {
            result.put(param.getName(), param.getValue());
        }
        return result;
    }

    @Override
    public Descriptor<DeploymentJob> getDescriptor() {
        return new DescriptorImpl();
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<DeploymentJob> {
        @Override
        public String getDisplayName() {
            return "Deployment Component";
        }
    }
}
