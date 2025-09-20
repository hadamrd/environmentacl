package io.jenkins.plugins.pulsar.environmentacl.model;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class VaultCredentialMapping implements Describable<VaultCredentialMapping> {
    private final String vaultId;
    private final String credentialId;
    private String description;

    @DataBoundConstructor
    public VaultCredentialMapping(String vaultId, String credentialId) {
        this.vaultId = vaultId;
        this.credentialId = credentialId;
    }

    public String getVaultId() {
        return vaultId;
    }

    public String getCredentialId() {
        return credentialId;
    }

    public String getDescription() {
        return description;
    }

    @DataBoundSetter
    public void setDescription(String description) {
        this.description = description;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<VaultCredentialMapping> {
        @Override
        public String getDisplayName() {
            return "Vault Credential Mapping";
        }
    }
}
