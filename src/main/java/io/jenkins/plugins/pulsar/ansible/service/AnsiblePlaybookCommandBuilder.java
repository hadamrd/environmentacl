package io.jenkins.plugins.pulsar.ansible.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builder for ansible-playbook commands.
 * Handles command construction with proper escaping and parameter formatting.
 */
public class AnsiblePlaybookCommandBuilder {
    
    private String playbook;
    private String user = "ansible";
    private String inventory;
    private String projectRoot;
    private Map<String, Object> extraVars;
    private String options;
    private final List<VaultConfig> vaultConfigs = new ArrayList<>();
    
    public static class VaultConfig {
        private final String vaultId;
        private final String passwordFile;
        
        public VaultConfig(String vaultId, String passwordFile) {
            this.vaultId = vaultId;
            this.passwordFile = passwordFile;
        }
        
        public String getVaultId() { return vaultId; }
        public String getPasswordFile() { return passwordFile; }
    }
    
    public AnsiblePlaybookCommandBuilder playbook(String playbook) {
        this.playbook = playbook;
        return this;
    }
    
    public AnsiblePlaybookCommandBuilder user(String user) {
        this.user = user != null ? user : "root";
        return this;
    }
    
    public AnsiblePlaybookCommandBuilder inventory(String inventory) {
        this.inventory = inventory;
        return this;
    }
    
    public AnsiblePlaybookCommandBuilder projectRoot(String projectRoot) {
        this.projectRoot = projectRoot;
        return this;
    }
    
    public AnsiblePlaybookCommandBuilder extraVars(Map<String, Object> extraVars) {
        this.extraVars = extraVars;
        return this;
    }
    
    public AnsiblePlaybookCommandBuilder options(String options) {
        this.options = options;
        return this;
    }
    
    public AnsiblePlaybookCommandBuilder vault(String vaultId, String passwordFile) {
        this.vaultConfigs.add(new VaultConfig(vaultId, passwordFile));
        return this;
    }
    
    /**
     * Build the complete command including directory change
     */
    public String buildFullCommand() {
        if (playbook == null) {
            throw new IllegalStateException("Playbook is required");
        }
        
        List<String> commandParts = new ArrayList<>();
        
        commandParts.add("set -e");
        
        // Change to project directory
        if (projectRoot != null) {
            commandParts.add("cd " + projectRoot);
        }
        
        // Build ansible-playbook command
        commandParts.add(buildAnsibleCommand());
        
        return String.join(" && ", commandParts);
    }
    
    /**
     * Build just the ansible-playbook command (without cd)
     */
    public String buildAnsibleCommand() {
        if (playbook == null) {
            throw new IllegalStateException("Playbook is required");
        }
        
        List<String> cmd = new ArrayList<>();
        
        // Base command
        cmd.add("ansible-playbook " + playbook);
        
        // User
        cmd.add("-u " + user);
        
        // Inventory
        if (inventory != null) {
            cmd.add("-i '" + inventory + "'");
        }
        
        // Vault IDs
        for (VaultConfig vault : vaultConfigs) {
            cmd.add("--vault-id " + vault.getVaultId() + "@" + vault.getPasswordFile());
        }
        
        // Standard extra vars
        cmd.add("-e 'running_from_jenkins=true'");
        
        // Custom extra vars
        if (extraVars != null) {
            for (Map.Entry<String, Object> var : extraVars.entrySet()) {
                String escapedValue = escapeValue(var.getValue().toString());
                cmd.add("-e '" + var.getKey() + "=" + escapedValue + "'");
            }
        }
        
        // Additional options
        if (options != null && !options.trim().isEmpty()) {
            cmd.add(options.trim());
        }
        
        return String.join(" ", cmd);
    }
    
    /**
     * Get command as separate parts for easier testing/debugging
     */
    public List<String> buildCommandParts() {
        List<String> parts = new ArrayList<>();
        
        if (projectRoot != null) {
            parts.add("cd " + projectRoot);
        }
        
        parts.add(buildAnsibleCommand());
        
        return parts;
    }
    
    /**
     * Escape special characters in values
     */
    private String escapeValue(String value) {
        if (value == null) {
            return "";
        }
        
        // Escape double quotes and backslashes
        return value.replaceAll("\\\\", "\\\\\\\\")
                   .replaceAll("\"", "\\\\\"")
                   .replaceAll("'", "\\\\'");
    }
    
    /**
     * Create a summary of the command for logging
     */
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Playbook: ").append(playbook);
        summary.append(", User: ").append(user);
        
        if (inventory != null) {
            summary.append(", Inventory: ").append(inventory);
        }
        
        if (!vaultConfigs.isEmpty()) {
            summary.append(", Vaults: ").append(vaultConfigs.size());
        }
        
        if (extraVars != null && !extraVars.isEmpty()) {
            summary.append(", Extra vars: ").append(extraVars.keySet());
        }
        
        if (options != null && !options.trim().isEmpty()) {
            summary.append(", Options: ").append(options);
        }
        
        return summary.toString();
    }
    
    /**
     * Validate that all required parameters are set
     */
    public void validate() {
        if (playbook == null || playbook.trim().isEmpty()) {
            throw new IllegalArgumentException("Playbook is required");
        }
        
        if (user == null || user.trim().isEmpty()) {
            throw new IllegalArgumentException("User is required");
        }
    }
}