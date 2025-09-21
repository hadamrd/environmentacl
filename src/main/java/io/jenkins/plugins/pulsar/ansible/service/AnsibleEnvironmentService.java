package io.jenkins.plugins.pulsar.ansible.service;

import io.jenkins.plugins.pulsar.ansible.AnsibleProjectsGlobalConfiguration;
import io.jenkins.plugins.pulsar.ansible.model.AnsibleEnvGroupConfig;
import io.jenkins.plugins.pulsar.ansible.model.AnsibleProject;
import io.jenkins.plugins.pulsar.environment.EnvironmentACLGlobalConfiguration;
import io.jenkins.plugins.pulsar.environment.model.EnvironmentGroup;

import java.util.ArrayList;
import java.util.List;

public class AnsibleEnvironmentService {
    private final AnsibleProjectsGlobalConfiguration ansibleConfig;
    private final EnvironmentACLGlobalConfiguration envAclConfig;

    public AnsibleEnvironmentService() {
        this.ansibleConfig = AnsibleProjectsGlobalConfiguration.get();
        this.envAclConfig = EnvironmentACLGlobalConfiguration.get();
    }

    /** Get SSH credential ID for an environment by looking up its group */
    public String getSshCredentialForEnvironment(String projectId, String envName) {
        // Find the environment group from Environment ACL plugin
        EnvironmentGroup envGroup = envAclConfig.getEnvironmentGroupForEnvironment(envName);
        if (envGroup != null) {
            return envGroup.getSshCredentialId();
        }
        return null;
    }

    /** Get the rendered inventory path for a specific environment */
    public String getInventoryPath(String projectId, String envName) {
        AnsibleProject project = ansibleConfig.getProjectById(projectId);
        if (project == null) {
            throw new IllegalArgumentException("Ansible project not found: " + projectId);
        }

        // Find which group this environment belongs to
        EnvironmentGroup envGroup = envAclConfig.getEnvironmentGroupForEnvironment(envName);
        if (envGroup == null) {
            throw new IllegalArgumentException("Environment not found in any group: " + envName);
        }

        // Find the Ansible environment mapping for this group
        AnsibleEnvGroupConfig ansibleEnv = project.getEnvironmentByGroup(envGroup.getName());
        if (ansibleEnv == null) {
            throw new IllegalArgumentException("No Ansible inventory mapping found for group: " + envGroup.getName());
        }

        return ansibleEnv.getRenderedInventoryPath(envName);
    }

    /** Get all environments accessible to an Ansible project (through mapped groups) */
    public List<String> getAccessibleEnvironments(String projectId) {
        AnsibleProject project = ansibleConfig.getProjectById(projectId);
        if (project == null) {
            return new ArrayList<>();
        }

        List<String> accessibleEnvs = new ArrayList<>();

        // For each environment group mapping in the Ansible project
        for (AnsibleEnvGroupConfig ansibleEnv : project.getEnvGroups()) {
            String groupName = ansibleEnv.getGroupName();

            // Find the corresponding environment group
            EnvironmentGroup envGroup = envAclConfig.getEnvironmentGroups().stream()
                    .filter(group -> groupName.equals(group.getName()))
                    .findFirst()
                    .orElse(null);

            if (envGroup != null) {
                accessibleEnvs.addAll(envGroup.getEnvironments());
            }
        }

        return accessibleEnvs;
    }

    /** Get environment group information for an environment */
    public EnvironmentGroup getEnvironmentGroupForEnvironment(String envName) {
        return envAclConfig.getEnvironmentGroupForEnvironment(envName);
    }

    /** Check if an Ansible project has configuration for a specific environment */
    public boolean hasConfigurationForEnvironment(String projectId, String envName) {
        try {
            getInventoryPath(projectId, envName);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
