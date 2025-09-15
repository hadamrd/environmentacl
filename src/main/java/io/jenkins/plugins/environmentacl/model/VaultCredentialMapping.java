package io.jenkins.plugins.environmentacl.model;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class VaultCredentialMapping extends AbstractDescribableImpl<VaultCredentialMapping> {
    private String vaultId;
    private String credentialId;
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
