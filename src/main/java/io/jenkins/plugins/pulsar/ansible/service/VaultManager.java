package io.jenkins.plugins.pulsar.ansible.service;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import hudson.AbortException;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.Secret;
import io.jenkins.plugins.pulsar.ansible.model.AnsibleVault;
import io.jenkins.plugins.pulsar.sharedcontainer.service.ContainerManager;
import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.util.*;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

/**
 * Manages Ansible vault files using rich domain objects. Works directly with AnsibleVault objects
 * instead of primitive strings.
 */
public class VaultManager implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String vaultDir;
    private final ContainerManager container;
    private final Map<String, AnsibleVault> setupVaults; // vaultId -> AnsibleVault

    public VaultManager(ContainerManager container) {
        this.container = container;
        this.vaultDir = "/ansible/vaults";
        this.setupVaults = new HashMap<>();
    }

    /** Setup vault files for the given vaults */
    public void setupVaultFiles(List<AnsibleVault> vaults, Run<?, ?> run, Launcher launcher, TaskListener listener)
            throws Exception {

        if (vaults == null || vaults.isEmpty()) {
            listener.getLogger().println("No vaults to setup");
            return;
        }

        listener.getLogger().println("Setting up " + vaults.size() + " vault files");

        // Create vault directory
        container.execute("mkdir -p " + vaultDir, launcher, listener);

        // Setup each vault
        for (AnsibleVault vault : vaults) {
            if (vault.getCredentialId() != null
                    && !vault.getCredentialId().trim().isEmpty()) {
                if (!setupVaults.containsKey(vault.getId())) {
                    setupVaultFile(vault, run, launcher, listener);
                    setupVaults.put(vault.getId(), vault);
                }
            }
        }

        listener.getLogger().println("Vault setup completed - " + setupVaults.size() + " vaults available");
    }

    /** Setup a single vault file using the rich AnsibleVault object */
    private void setupVaultFile(AnsibleVault vault, Run<?, ?> run, Launcher launcher, TaskListener listener)
            throws Exception {

        // Get credential using the vault's credentialId
        String vaultPassword = getSecretCredential(vault.getCredentialId(), run);
        if (vaultPassword == null) {
            listener.getLogger().println("Warning: Vault credential not found: " + vault.getCredentialId());
            return;
        }

        String vaultFilePath = getVaultFilePath(vault);

        // Command that reads from stdin and writes to file with secure permissions
        String secureWriteCmd = String.format("umask 077 && cat > %s && chmod 600 %s", vaultFilePath, vaultFilePath);

        // Create input stream with vault password
        ByteArrayInputStream passwordInput = new ByteArrayInputStream(vaultPassword.getBytes());

        // Execute securely with stdin - password not visible in logs or process list
        int result = container.execute(secureWriteCmd, null, null, passwordInput, launcher, listener);

        if (result == 0) {
            listener.getLogger()
                    .println("Vault file created: "
                            + vault.getId()
                            + (vault.getDescription() != null ? " (" + vault.getDescription() + ")" : ""));
        } else {
            throw new Exception("Failed to create vault file: " + vault.getId() + " (exit code: " + result + ")");
        }
    }

    /** Get the file path for a vault object */
    public String getVaultFilePath(AnsibleVault vault) {
        // Use vaultFile if specified, otherwise default to id-based naming
        if (vault.getVaultFile() != null && !vault.getVaultFile().trim().isEmpty()) {
            return vaultDir + "/" + vault.getVaultFile();
        } else {
            return vaultDir + "/" + vault.getId() + ".txt";
        }
    }

    /** Get all setup vault objects (not just IDs) */
    public Collection<AnsibleVault> getSetupVaults() {
        return new ArrayList<>(setupVaults.values());
    }

    /** Get setup vault names */
    public Set<String> getSetupVaultNames() {
        return new HashSet<>(setupVaults.keySet());
    }

    /** Check if a vault is setup */
    public boolean isVaultSetup(String vaultId) {
        return setupVaults.containsKey(vaultId);
    }

    /** Get a specific setup vault by ID */
    public AnsibleVault getSetupVault(String vaultId) {
        return setupVaults.get(vaultId);
    }

    /** Get vault file paths for ansible-playbook command building */
    public Map<String, String> getVaultFilePaths() {
        Map<String, String> vaultPaths = new HashMap<>();
        for (AnsibleVault vault : setupVaults.values()) {
            vaultPaths.put(vault.getId(), getVaultFilePath(vault));
        }
        return vaultPaths;
    }

    /** Cleanup vault files (optional - container cleanup usually handles this) */
    public void cleanup(Launcher launcher, TaskListener listener) {
        if (setupVaults.isEmpty()) {
            return;
        }

        try {
            // Remove vault directory and all files
            container.execute("rm -rf " + vaultDir, launcher, listener);
            setupVaults.clear();
            listener.getLogger().println("Vault files cleaned up");
        } catch (Exception e) {
            listener.getLogger().println("Warning: Failed to cleanup vault files: " + e.getMessage());
        }
    }

    /** Get secret credential value */
    private String getSecretCredential(String credentialId, Run<?, ?> run) {
        try {
            StringCredentials stringCredential =
                    CredentialsProvider.findCredentialById(credentialId, StringCredentials.class, run);
            if (stringCredential == null) {
                throw new AbortException("Credential for vault not found, make sure it is configured in Jenkins");
            }
            return Secret.toString(stringCredential.getSecret());
        } catch (Exception e) {
            return null;
        }
    }

    // Getters
    public String getVaultDir() {
        return vaultDir;
    }

    public boolean hasVaults() {
        return !setupVaults.isEmpty();
    }
}
