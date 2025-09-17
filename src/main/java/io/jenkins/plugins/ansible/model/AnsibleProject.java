package io.jenkins.plugins.ansible.model;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import java.util.ArrayList;
import java.util.List;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class AnsibleProject extends AbstractDescribableImpl<AnsibleProject> {
    private String id;
    private String repository;
    private String execEnv;
    private String azureCredentialId;
    private String defaultBranch;
    private AnsibleConfig ansibleConfig;
    private List<AnsibleVault> vaults;
    private List<AnsibleEnvironment> environments;

    @DataBoundConstructor
    public AnsibleProject(String id, String repository) {
        this.id = id;
        this.repository = repository;
        this.defaultBranch = "main";
        this.ansibleConfig = new AnsibleConfig();
        this.vaults = new ArrayList<>();
        this.environments = new ArrayList<>();
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getRepository() {
        return repository;
    }

    public String getExecEnv() {
        return execEnv;
    }

    public String getAzureCredentialId() {
        return azureCredentialId;
    }

    public String getDefaultBranch() {
        return defaultBranch != null ? defaultBranch : "main";
    }

    public AnsibleConfig getAnsibleConfig() {
        return ansibleConfig != null ? ansibleConfig : new AnsibleConfig();
    }

    public List<AnsibleVault> getVaults() {
        return vaults != null ? vaults : new ArrayList<>();
    }

    public List<AnsibleEnvironment> getEnvironments() {
        return environments != null ? environments : new ArrayList<>();
    }

    // Setters
    @DataBoundSetter
    public void setExecEnv(String execEnv) {
        this.execEnv = execEnv;
    }

    @DataBoundSetter
    public void setAzureCredentialId(String azureCredentialId) {
        this.azureCredentialId = azureCredentialId;
    }

    @DataBoundSetter
    public void setDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }

    @DataBoundSetter
    public void setAnsibleConfig(AnsibleConfig ansibleConfig) {
        this.ansibleConfig = ansibleConfig != null ? ansibleConfig : new AnsibleConfig();
    }

    @DataBoundSetter
    public void setVaults(List<AnsibleVault> vaults) {
        this.vaults = vaults != null ? vaults : new ArrayList<>();
    }

    @DataBoundSetter
    public void setEnvironments(List<AnsibleEnvironment> environments) {
        this.environments = environments != null ? environments : new ArrayList<>();
    }

    // Helper methods
    public AnsibleVault getVaultByName(String vaultName) {
        return getVaults().stream()
                .filter(vault -> vaultName.equals(vault.getName()))
                .findFirst()
                .orElse(null);
    }

    public AnsibleEnvironment getEnvironmentByGroup(String group) {
        return getEnvironments().stream()
                .filter(env -> group.equals(env.getGroup()))
                .findFirst()
                .orElse(null);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<AnsibleProject> {
        @Override
        public String getDisplayName() {
            return "Ansible Project";
        }
    }
}
