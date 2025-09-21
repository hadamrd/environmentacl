package io.jenkins.plugins.pulsar.ansible.model;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class AnsibleEnvGroupConfig implements Describable<AnsibleEnvGroupConfig>, Serializable {
    private static final long serialVersionUID = 1L;

    private String groupName;
    private String description;
    private String inventoryPathTemplate;
    private List<String> sshKeys;
    private List<String> vaultIds;

    @DataBoundConstructor
    public AnsibleEnvGroupConfig(String group) {
        this.groupName = group;
        this.sshKeys = new ArrayList<>();
        this.vaultIds = new ArrayList<>();
    }

    public String getGroupName() {
        return groupName;
    }

    public String getDescription() {
        return description;
    }

    public String getInventoryPathTemplate() {
        return inventoryPathTemplate;
    }

    public List<String> getSshKeys() {
        return sshKeys != null ? sshKeys : new ArrayList<>();
    }

    public List<String> getVaultIds() {
        return vaultIds != null ? vaultIds : new ArrayList<>();
    }

    @DataBoundSetter
    public void setGroupName(String group) {
        this.groupName = group;
    }

    @DataBoundSetter
    public void setDescription(String description) {
        this.description = description;
    }

    @DataBoundSetter
    public void setInventoryPathTemplate(String inventoryPath) {
        this.inventoryPathTemplate = inventoryPath;
    }

    @DataBoundSetter
    public void setSshKeys(List<String> sshKeys) {
        this.sshKeys = sshKeys != null ? sshKeys : new ArrayList<>();
    }

    @DataBoundSetter
    public void setVaultIds(List<String> vaults) {
        this.vaultIds = vaults != null ? vaults : new ArrayList<>();
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<AnsibleEnvGroupConfig> {
        @Override
        public String getDisplayName() {
            return "Ansible Environment";
        }
    }

    public String getRenderedInventoryPath(String envName) {
        String template = getInventoryPathTemplate();
        // Simple template replacement - {{env}} becomes the actual env name
        return template.replace("{{env}}", envName.toLowerCase());
    }
}
