package io.jenkins.plugins.pulsar.ansible.model;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import io.jenkins.plugins.pulsar.environmentacl.EnvironmentACLGlobalConfiguration;
import io.jenkins.plugins.pulsar.environmentacl.model.EnvironmentGroup;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class AnsibleProject implements Describable<AnsibleProject>, Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String repository;
    private String execEnv;
    private String azureCredentialId;
    private String defaultBranch;
    private String ansibleConfig;
    private List<AnsibleVault> vaults;
    private List<AnsibleEnvGroupConfig> envGroups;

    @DataBoundConstructor
    public AnsibleProject(String id, String repository) {
        this.id = id;
        this.repository = repository;
        this.defaultBranch = "main";
        this.ansibleConfig = getDefaultAnsibleConfig();
        this.vaults = new ArrayList<>();
        this.envGroups = new ArrayList<>();
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
        return """
               [defaults]
               host_key_checking = False
               gathering = implicit
               timeout = 10
               retries = 3
               pipelining = True
               stdout_callback = default
               nocows = 1

               [ssh_connection]
               ssh_args = -o ControlMaster=auto -o ControlPersist=60s
               pipelining = True
               """;
    }

    public List<AnsibleVault> getVaults() {
        return vaults != null ? vaults : new ArrayList<>();
    }

    public List<AnsibleEnvGroupConfig> getEnvGroups() {
        return envGroups != null ? envGroups : new ArrayList<>();
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
    public void setEnvGroups(List<AnsibleEnvGroupConfig> environments) {
        this.envGroups = environments != null ? environments : new ArrayList<>();
    }

    // Helper methods
    public AnsibleVault getVaultByName(String vaultName) {
        return getVaults().stream()
                .filter(vault -> vaultName.equals(vault.getId()))
                .findFirst()
                .orElse(null);
    }

    public AnsibleVault getVaultById(String vaultId) {
        return getVaults().stream()
                .filter(vault -> vaultId.equals(vault.getId()))
                .findFirst()
                .orElse(null);
    }

    public AnsibleEnvGroupConfig getEnvironmentByGroup(String group) {
        return getEnvGroups().stream()
                .filter(env -> group.equals(env.getGroupName()))
                .findFirst()
                .orElse(null);
    }

    public List<AnsibleVault> getEnvVaults(String envName) {
        EnvironmentGroup envGroup = EnvironmentACLGlobalConfiguration.get().getEnvironmentGroupForEnvironment(envName);

        if (envGroup == null) {
            return new ArrayList<>();
        }

        List<String> groupVaultIds = getEnvGroups().stream()
                .filter(env -> envGroup.getName().equals(env.getGroupName()))
                .flatMap(env -> env.getVaultIds().stream())
                .distinct()
                .collect(Collectors.toList());

        return groupVaultIds.stream()
                .map(this::getVaultById)
                .filter(vault -> vault != null)
                .collect(Collectors.toList());
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<AnsibleProject> {
        @Override
        public String getDisplayName() {
            return "Ansible Project";
        }
    }
}
