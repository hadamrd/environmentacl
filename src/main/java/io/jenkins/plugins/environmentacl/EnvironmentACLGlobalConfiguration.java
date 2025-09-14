package io.jenkins.plugins.environmentacl;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.verb.POST;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import hudson.Extension;
import hudson.util.FormValidation;
import io.jenkins.plugins.environmentacl.model.EnvironmentACLConfig;
import io.jenkins.plugins.environmentacl.model.EnvironmentACLConfig.ACLRuleConfig;
import io.jenkins.plugins.environmentacl.model.EnvironmentACLConfig.EnvironmentGroupConfig;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;

/** Global configuration for Environment ACL Manager using Jackson POJOs directly */
@Extension
public class EnvironmentACLGlobalConfiguration extends GlobalConfiguration {

    private static final Logger LOGGER = Logger.getLogger(EnvironmentACLGlobalConfiguration.class.getName());
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    private EnvironmentACLConfig config = new EnvironmentACLConfig();
    private String yamlConfiguration = "";

    public EnvironmentACLGlobalConfiguration() {
        load();
    }

    /** Get the singleton instance of this configuration */
    public static EnvironmentACLGlobalConfiguration get() {
        EnvironmentACLGlobalConfiguration config =
                GlobalConfiguration.all().get(EnvironmentACLGlobalConfiguration.class);
        if (config == null) {
            config = new EnvironmentACLGlobalConfiguration();
            GlobalConfiguration.all().add(config);
        }
        return config;
    }

    // ========== Getters and Setters ==========

    public List<EnvironmentGroupConfig> getEnvironmentGroups() {
        LOGGER.log(Level.INFO, "Get - Environment groups: {0}", config.environmentGroups != null ? config.environmentGroups.size() : "null");
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
        LOGGER.info("=== GlobalConfiguration.configure called ===");
        try {
            // Handle YAML configuration
            String newYaml = formData.getString("yamlConfiguration");
            LOGGER.log(Level.INFO, "Configure method received YAML: {0} chars", newYaml != null ? newYaml.length() : 0);

            if (newYaml != null && !newYaml.trim().isEmpty()) {
                parseYamlConfiguration(newYaml);
                this.yamlConfiguration = newYaml;
                LOGGER.info("Parsed and set YAML in configure method");
            }

            save();
            LOGGER.info("Called save() in configure method");
            return true;
        } catch (JsonProcessingException | FormException e) {
            LOGGER.log(Level.SEVERE, "Failed to save configuration in configure method: {0}", e.getMessage());
            throw new FormException("Failed to save configuration: " + e.getMessage(), "yamlConfiguration");
        }
    }

    @Override
    public synchronized void save() {
        LOGGER.info("=== GlobalConfiguration.save() called ===");
        LOGGER.log(
                Level.INFO,
                "About to save - Environment groups: {0}",
                getEnvironmentGroups().size());
        LOGGER.log(Level.INFO, "About to save - ACL rules: {0}", getAclRules().size());
        LOGGER.log(Level.INFO, "About to save - YAML config length: {0}", yamlConfiguration.length());

        super.save();

        LOGGER.info("=== GlobalConfiguration.save() finished ===");
    }

    @Override
    public synchronized void load() {
        LOGGER.info("=== GlobalConfiguration.load() called ===");

        super.load();

        LOGGER.log(Level.INFO, "After load - Environment groups: {0}", config.environmentGroups != null ? config.environmentGroups.size() : "null");
        LOGGER.log(Level.INFO, "After load - ACL rules: {0}", config.rules != null ? config.rules.size() : "null");
        LOGGER.log(Level.INFO, "After load - YAML config length: {0}", yamlConfiguration.length());
        LOGGER.info("=== GlobalConfiguration.load() finished ===");
    }

    /** Parse YAML configuration using Jackson directly - no more duplication! */
    public void parseYamlConfiguration(String yamlContent) throws FormException, JsonProcessingException {
        if (yamlContent == null || yamlContent.trim().isEmpty()) {
            this.config = new EnvironmentACLConfig();
            return;
        }

        this.config = YAML_MAPPER.readValue(yamlContent, EnvironmentACLConfig.class);
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
        } catch (JsonProcessingException | FormException e) {
            return FormValidation.error("Invalid YAML: " + e.getMessage());
        }
    }

    // ========== API Methods for Other Components ==========

    /** Get all environments from all groups */
    public List<String> getAllEnvironments() {
        return config.environmentGroups.stream()
                .flatMap(group -> group.environments.stream())
                .distinct()
                .collect(Collectors.toList());
    }

    /** Get all environment group names */
    public List<String> getAllEnvironmentGroups() {
        return config.environmentGroups.stream().map(group -> group.name).collect(Collectors.toList());
    }

    /** Find environment group by name */
    public EnvironmentGroupConfig getEnvironmentGroupByName(String name) {
        return config.environmentGroups.stream()
                .filter(group -> group.name.equals(name))
                .findFirst()
                .orElse(null);
    }

    /** Find environment group that contains the specified environment */
    public EnvironmentGroupConfig getEnvironmentGroupForEnvironment(String environment) {
        return config.environmentGroups.stream()
                .filter(group -> group.environments.contains(environment))
                .findFirst()
                .orElse(null);
    }

    /** Get SSH key IDs for a specific environment */
    public List<String> getSshKeysForEnvironment(String environment) {
        EnvironmentGroupConfig group = getEnvironmentGroupForEnvironment(environment);
        return group != null ? new ArrayList<>(group.sshKeys) : new ArrayList<>();
    }

    /** Get vault key IDs for a specific environment */
    public List<String> getVaultKeysForEnvironment(String environment) {
        EnvironmentGroupConfig group = getEnvironmentGroupForEnvironment(environment);
        return group != null ? new ArrayList<>(group.vaultKeys) : new ArrayList<>();
    }

    /** Get node labels for a specific environment */
    public List<String> getNodeLabelsForEnvironment(String environment) {
        EnvironmentGroupConfig group = getEnvironmentGroupForEnvironment(environment);
        return group != null ? new ArrayList<>(group.nodeLabels) : new ArrayList<>();
    }
}
