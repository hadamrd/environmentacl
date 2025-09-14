package io.jenkins.plugins.environmentacl.step;

import io.jenkins.plugins.environmentacl.model.EnvironmentGroup;
import io.jenkins.plugins.environmentacl.service.CredentialService;

import java.io.Serializable;
import java.util.List;

public class EnvironmentAccessContext implements Serializable {
    private final String environment;
    private final boolean hasAccess;
    private final EnvironmentGroup environmentGroup;
    private final String sshCredentialId;
    private final transient CredentialService credentialService;

    public EnvironmentAccessContext(String environment, boolean hasAccess, 
                                  EnvironmentGroup environmentGroup, String sshCredentialId,
                                  CredentialService credentialService) {
        this.environment = environment;
        this.hasAccess = hasAccess;
        this.environmentGroup = environmentGroup;
        this.sshCredentialId = sshCredentialId;
        this.credentialService = credentialService;
    }

    public String getEnvironment() {
        return environment;
    }

    public boolean hasAccess() {
        return hasAccess;
    }

    public EnvironmentGroup getEnvironmentGroup() {
        return environmentGroup;
    }

    public String getSshCredentialId() {
        return sshCredentialId;
    }

    public String getVaultCredentialId(String vaultId) {
        if (credentialService != null) {
            return credentialService.getVaultCredential(environment, vaultId);
        }
        return environmentGroup != null ? environmentGroup.getVaultCredentialId(vaultId) : null;
    }

    public List<String> getEnvironments() {
        return environmentGroup != null ? environmentGroup.getEnvironments() : null;
    }

    public String getGroupName() {
        return environmentGroup != null ? environmentGroup.getName() : null;
    }
}