package io.jenkins.plugins.environmentacl.service;

import io.jenkins.plugins.environmentacl.EnvironmentACLGlobalConfiguration;
import io.jenkins.plugins.environmentacl.model.EnvironmentGroup;

public class CredentialService {
    private final EnvironmentACLGlobalConfiguration config;

    public CredentialService() {
        this.config = EnvironmentACLGlobalConfiguration.get();
    }

    public String getSshCredentialForEnvironment(String environment) {
        EnvironmentGroup group = config.getEnvironmentGroupForEnvironment(environment);
        return group != null ? group.getSshCredentialId() : null;
    }

    public String getVaultCredential(String environment, String vaultId) {
        return config.getVaultCredentialId(environment, vaultId);
    }

    public EnvironmentGroup getEnvironmentGroup(String environment) {
        return config.getEnvironmentGroupForEnvironment(environment);
    }
}
