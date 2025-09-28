package io.jenkins.plugins.pulsar.environment.model;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class EnvironmentGroup implements Describable<EnvironmentGroup>, Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    private String description;
    private List<String> environments;
    private List<String> tags;
    private String sshCredentialId;
    private List<VaultCredentialMapping> vaultCredentials;
    private List<String> nodeLabels;

    @DataBoundConstructor
    public EnvironmentGroup(String name) {
        this.name = name;
        this.environments = new ArrayList<>();
        this.tags = new ArrayList<>();
        this.vaultCredentials = new ArrayList<>();
        this.nodeLabels = new ArrayList<>(); // Initialize node labels
    }

    // Existing getters and setters...
    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    @DataBoundSetter
    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getEnvironments() {
        return environments != null ? environments : new ArrayList<>();
    }

    @DataBoundSetter
    public void setEnvironments(List<String> environments) {
        this.environments = environments != null ? environments : new ArrayList<>();
    }

    public List<String> getTags() {
        return tags != null ? tags : new ArrayList<>();
    }

    @DataBoundSetter
    public void setTags(List<String> tags) {
        this.tags = tags != null ? tags : new ArrayList<>();
    }

    public String getSshCredentialId() {
        return sshCredentialId;
    }

    @DataBoundSetter
    public void setSshCredentialId(String sshCredentialId) {
        this.sshCredentialId = sshCredentialId;
    }

    public List<VaultCredentialMapping> getVaultCredentials() {
        return vaultCredentials != null ? vaultCredentials : new ArrayList<>();
    }

    @DataBoundSetter
    public void setVaultCredentials(List<VaultCredentialMapping> vaultCredentials) {
        this.vaultCredentials = vaultCredentials != null ? vaultCredentials : new ArrayList<>();
    }

    public List<String> getNodeLabels() {
        return nodeLabels != null ? nodeLabels : new ArrayList<>();
    }

    @DataBoundSetter
    public void setNodeLabels(List<String> nodeLabels) {
        this.nodeLabels = nodeLabels != null ? nodeLabels : new ArrayList<>();
    }

    // Helper method to get vault credential by vaultId
    public String getVaultCredentialId(String vaultId) {
        return getVaultCredentials().stream()
                .filter(mapping -> vaultId.equals(mapping.getVaultId()))
                .map(VaultCredentialMapping::getCredentialId)
                .findFirst()
                .orElse(null);
    }

    public String getNodeLabelsAsString() {
        List<String> labels = getNodeLabels();
        if (labels.isEmpty()) {
            return "master"; // Default fallback
        }
        return String.join(" && ", labels); // Jenkins label expression format
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<EnvironmentGroup> {
        @Override
        public String getDisplayName() {
            return "Environment Group";
        }
    }
}
