package io.jenkins.plugins.pulsar.ansible.model;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class AnsibleProject extends AbstractDescribableImpl<AnsibleProject> implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String repository;
    private String execEnv;
    private String azureCredentialId;
    private String defaultBranch;
    private String ansibleConfig;
    private List<AnsibleVault> vaults;
    private List<AnsibleEnvironment> environments;

    @DataBoundConstructor
    public AnsibleProject(String id, String repository) {
        this.id = id;
        this.repository = repository;
        this.defaultBranch = "main";
        this.ansibleConfig = getDefaultAnsibleConfig();
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

    public String getAnsibleConfig() {
        return ansibleConfig != null ? ansibleConfig : getDefaultAnsibleConfig();
    }

    @DataBoundSetter
    public void setAnsibleConfig(String ansibleConfig) {
        this.ansibleConfig = ansibleConfig;
    }

    private String getDefaultAnsibleConfig() {
        return "[defaults]\n"
                + "host_key_checking = False\n"
                + "gathering = implicit\n"
                + "timeout = 10\n"
                + "retries = 3\n"
                + "pipelining = True\n"
                + "stdout_callback = default\n"
                + "nocows = 1\n"
                + "\n[ssh_connection]\n"
                + "ssh_args = -o ControlMaster=auto -o ControlPersist=60s\n"
                + "pipelining = True\n";
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
