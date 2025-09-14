package io.jenkins.plugins.environmentacl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import hudson.Extension;
import hudson.util.FormValidation;
import io.jenkins.plugins.environmentacl.model.EnvironmentACLConfig;
import io.jenkins.plugins.environmentacl.model.EnvironmentACLConfig.EnvironmentGroupConfig;
import io.jenkins.plugins.environmentacl.model.EnvironmentACLConfig.ACLRuleConfig;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Global configuration for Environment ACL Manager using Jackson POJOs directly
 */
@Extension
public class EnvironmentACLGlobalConfiguration extends GlobalConfiguration {
    
    private static final Logger LOGGER = Logger.getLogger(EnvironmentACLGlobalConfiguration.class.getName());
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    
    private EnvironmentACLConfig config = new EnvironmentACLConfig();
    private String yamlConfiguration = "";
    
    public EnvironmentACLGlobalConfiguration() {
        load();
    }
    
    /**
     * Get the singleton instance of this configuration
     */
    @Nonnull
    public static EnvironmentACLGlobalConfiguration get() {
        EnvironmentACLGlobalConfiguration config = GlobalConfiguration.all().get(EnvironmentACLGlobalConfiguration.class);
        if (config == null) {
            config = new EnvironmentACLGlobalConfiguration();
            GlobalConfiguration.all().add(config);
        }
        return config;
    }
    
    // ========== Getters and Setters ==========
    
    public List<EnvironmentGroupConfig> getEnvironmentGroups() {
        return config.environmentGroups;
    }
    
    public List<ACLRuleConfig> getAclRules() {
        return config.rules;
    }
    
    public String getYamlConfiguration() {
        return yamlConfiguration;
    }
    
    public void setYamlConfiguration(String yamlConfiguration) {
        this.yamlConfiguration = yamlConfiguration != null ? yamlConfiguration : "";
    }
    
    // ========== Configuration Management ==========
    
    @Override
    public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
        try {
            // Handle YAML configuration
            String newYaml = formData.getString("yamlConfiguration");
            if (newYaml != null && !newYaml.trim().isEmpty()) {
                parseYamlConfiguration(newYaml);
                this.yamlConfiguration = newYaml;
            }
            
            save();
            return true;
        } catch (Exception e) {
            LOGGER.severe("Failed to save configuration: " + e.getMessage());
            throw new FormException("Failed to save configuration: " + e.getMessage(), "yamlConfiguration");
        }
    }
    
    /**
     * Parse YAML configuration using Jackson directly - no more duplication!
     */
    public void parseYamlConfiguration(String yamlContent) throws FormException {
        try {
            if (yamlContent == null || yamlContent.trim().isEmpty()) {
                this.config = new EnvironmentACLConfig();
                return;
            }
            
            // That's it - one line!
            this.config = YAML_MAPPER.readValue(yamlContent, EnvironmentACLConfig.class);
            
        } catch (Exception e) {
            throw new FormException("Invalid YAML: " + e.getMessage(), "yamlConfiguration");
        }
    }
    
    // ========== Validation ==========
    
    @POST
    public FormValidation doCheckYamlConfiguration(@QueryParameter String yamlConfiguration) {
        if (yamlConfiguration == null || yamlConfiguration.trim().isEmpty()) {
            return FormValidation.warning("YAML configuration is empty");
        }
        
        try {
            parseYamlConfiguration(yamlConfiguration);
            return FormValidation.ok("YAML configuration is valid");
        } catch (Exception e) {
            return FormValidation.error("Invalid YAML: " + e.getMessage());
        }
    }
    
    // ========== API Methods for Other Components ==========
    
    /**
     * Get all environments from all groups
     */
    public List<String> getAllEnvironments() {
        return config.environmentGroups.stream()
                .flatMap(group -> group.environments.stream())
                .distinct()
                .collect(Collectors.toList());
    }
    
    /**
     * Get all environment group names
     */
    public List<String> getAllEnvironmentGroups() {
        return config.environmentGroups.stream()
                .map(group -> group.name)
                .collect(Collectors.toList());
    }
    
    /**
     * Find environment group by name
     */
    @CheckForNull
    public EnvironmentGroupConfig getEnvironmentGroupByName(String name) {
        return config.environmentGroups.stream()
                .filter(group -> group.name.equals(name))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Find environment group that contains the specified environment
     */
    @CheckForNull
    public EnvironmentGroupConfig getEnvironmentGroupForEnvironment(String environment) {
        return config.environmentGroups.stream()
                .filter(group -> group.environments.contains(environment))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Get SSH key IDs for a specific environment
     */
    public List<String> getSshKeysForEnvironment(String environment) {
        EnvironmentGroupConfig group = getEnvironmentGroupForEnvironment(environment);
        return group != null ? new ArrayList<>(group.sshKeys) : new ArrayList<>();
    }
    
    /**
     * Get vault key IDs for a specific environment
     */
    public List<String> getVaultKeysForEnvironment(String environment) {
        EnvironmentGroupConfig group = getEnvironmentGroupForEnvironment(environment);
        return group != null ? new ArrayList<>(group.vaultKeys) : new ArrayList<>();
    }
    
    /**
     * Get node labels for a specific environment
     */
    public List<String> getNodeLabelsForEnvironment(String environment) {
        EnvironmentGroupConfig group = getEnvironmentGroupForEnvironment(environment);
        return group != null ? new ArrayList<>(group.nodeLabels) : new ArrayList<>();
    }
}