package io.jenkins.plugins.environmentacl.model;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.ArrayList;
import java.util.List;

public class EnvironmentGroup extends AbstractDescribableImpl<EnvironmentGroup> {
    private String name;
    private String description;
    private List<String> environments;
    private List<String> tags; // New: Environment tags for enterprise filtering
    private String sshCredentialId;
    private List<VaultCredentialMapping> vaultCredentials;

    @DataBoundConstructor
    public EnvironmentGroup(String name) {
        this.name = name;
        this.environments = new ArrayList<>();
        this.tags = new ArrayList<>();
        this.vaultCredentials = new ArrayList<>();
    }

    // Getters and setters
    public String getName() { return name; }
    
    public String getDescription() { return description; }
    
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

    public String getSshCredentialId() { return sshCredentialId; }

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

    // Helper method to get vault credential by vaultId
    public String getVaultCredentialId(String vaultId) {
        return getVaultCredentials().stream()
                .filter(mapping -> vaultId.equals(mapping.getVaultId()))
                .map(VaultCredentialMapping::getCredentialId)
                .findFirst()
                .orElse(null);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<EnvironmentGroup> {
        @Override
        public String getDisplayName() {
            return "Environment Group";
        }
    }
}