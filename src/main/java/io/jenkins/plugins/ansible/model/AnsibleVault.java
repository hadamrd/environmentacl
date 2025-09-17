package io.jenkins.plugins.ansible.model;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class AnsibleVault extends AbstractDescribableImpl<AnsibleVault> {
    private String name;
    private String description;
    private String credentialId;
    private String vaultFile;

    @DataBoundConstructor
    public AnsibleVault(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCredentialId() {
        return credentialId;
    }

    public String getVaultFile() {
        return vaultFile;
    }

    @DataBoundSetter
    public void setDescription(String description) {
        this.description = description;
    }

    @DataBoundSetter
    public void setCredentialId(String credentialId) {
        this.credentialId = credentialId;
    }

    @DataBoundSetter
    public void setVaultFile(String vaultFile) {
        this.vaultFile = vaultFile;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<AnsibleVault> {
        @Override
        public String getDisplayName() {
            return "Ansible Vault";
        }
    }
}
