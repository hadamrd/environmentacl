package io.jenkins.plugins.ansible.model;

import java.util.ArrayList;
import java.util.List;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;

public class AnsibleEnvironment extends AbstractDescribableImpl<AnsibleEnvironment> {
    private String category;
    private String description;
    private String inventoryPath;
    private List<String> sshKeys;
    private List<String> vaults;

    @DataBoundConstructor
    public AnsibleEnvironment(String category) {
        this.category = category;
        this.sshKeys = new ArrayList<>();
        this.vaults = new ArrayList<>();
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public String getInventoryPath() {
        return inventoryPath;
    }

    public List<String> getSshKeys() {
        return sshKeys != null ? sshKeys : new ArrayList<>();
    }

    public List<String> getVaults() {
        return vaults != null ? vaults : new ArrayList<>();
    }

    @DataBoundSetter
    public void setDescription(String description) {
        this.description = description;
    }

    @DataBoundSetter
    public void setInventoryPath(String inventoryPath) {
        this.inventoryPath = inventoryPath;
    }

    @DataBoundSetter
    public void setSshKeys(List<String> sshKeys) {
        this.sshKeys = sshKeys != null ? sshKeys : new ArrayList<>();
    }

    @DataBoundSetter
    public void setVaults(List<String> vaults) {
        this.vaults = vaults != null ? vaults : new ArrayList<>();
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<AnsibleEnvironment> {
        @Override
        public String getDisplayName() {
            return "Ansible Environment";
        }
    }
}