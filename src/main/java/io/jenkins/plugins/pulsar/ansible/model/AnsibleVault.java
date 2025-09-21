package io.jenkins.plugins.pulsar.ansible.model;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import java.io.Serializable;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class AnsibleVault implements  Describable<AnsibleVault>, Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String description;
    private String credentialId;
    private String vaultFile;

    @DataBoundConstructor
    public AnsibleVault(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
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
    public void setId(String id) {
        this.id = id;
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
