package io.jenkins.plugins.environmentacl;

import hudson.Extension;
import hudson.model.ManagementLink;
import hudson.security.Permission;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.verb.POST;
import org.yaml.snakeyaml.Yaml;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Extension
public class EnvironmentACLGlobalConfiguration extends GlobalConfiguration {
    
    private static final Logger LOGGER = Logger.getLogger(EnvironmentACLGlobalConfiguration.class.getName());
    
    private List<EnvironmentGroup> environmentGroups = new ArrayList<>();
    private List<ACLRule> aclRules = new ArrayList<>();
    private String yamlConfiguration = "";
    
    public EnvironmentACLGlobalConfiguration() {
        load();
    }
    
    @Nonnull
    public static EnvironmentACLGlobalConfiguration get() {
        EnvironmentACLGlobalConfiguration config = GlobalConfiguration.all().get(EnvironmentACLGlobalConfiguration.class);
        if (config == null) {
            throw new IllegalStateException("EnvironmentACLGlobalConfiguration not found");
        }
        return config;
    }
    
    public List<EnvironmentGroup> getEnvironmentGroups() {
        return environmentGroups;
    }
    
    public void setEnvironmentGroups(List<EnvironmentGroup> environmentGroups) {
        this.environmentGroups = environmentGroups != null ? environmentGroups : new ArrayList<>();
    }
    
    public List<ACLRule> getAclRules() {
        return aclRules;
    }
    
    public void setAclRules(List<ACLRule> aclRules) {
        this.aclRules = aclRules != null ? aclRules : new ArrayList<>();
    }
    
    public String getYamlConfiguration() {
        return yamlConfiguration;
    }
    
    public void setYamlConfiguration(String yamlConfiguration) {
        this.yamlConfiguration = yamlConfiguration != null ? yamlConfiguration : "";
    }
    
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
    
    @SuppressWarnings("unchecked")
    private void parseYamlConfiguration(String yamlContent) throws FormException {
        try {
            Yaml yaml = new Yaml();
            Map<String, Object> config = yaml.load(yamlContent);
            
            // Parse environment groups
            List<EnvironmentGroup> newGroups = new ArrayList<>();
            if (config.containsKey("environmentGroups")) {
                List<Map<String, Object>> groups = (List<Map<String, Object>>) config.get("environmentGroups");
                for (Map<String, Object> groupData : groups) {
                    EnvironmentGroup group = parseEnvironmentGroup(groupData);
                    newGroups.add(group);
                }
            }
            
            // Parse ACL rules
            List<ACLRule> newRules = new ArrayList<>();
            if (config.containsKey("rules")) {
                List<Map<String, Object>> rules = (List<Map<String, Object>>) config.get("rules");
                for (Map<String, Object> ruleData : rules) {
                    ACLRule rule = parseACLRule(ruleData);
                    newRules.add(rule);
                }
            }
            
            this.environmentGroups = newGroups;
            this.aclRules = newRules;
            
        } catch (Exception e) {
            throw new FormException("Invalid YAML configuration: " + e.getMessage(), "yamlConfiguration");
        }
    }
    
    @SuppressWarnings("unchecked")
    private EnvironmentGroup parseEnvironmentGroup(Map<String, Object> data) {
        String name = (String) data.get("name");
        String description = (String) data.get("description");
        List<String> environments = (List<String>) data.getOrDefault("environments", new ArrayList<>());
        List<String> sshKeyIds = (List<String>) data.getOrDefault("sshKeys", new ArrayList<>());
        List<String> vaultKeyIds = (List<String>) data.getOrDefault("vaultKeys", new ArrayList<>());
        List<String> nodeLabels = (List<String>) data.getOrDefault("nodeLabels", new ArrayList<>());
        
        return new EnvironmentGroup(name, description, environments, sshKeyIds, vaultKeyIds, nodeLabels);
    }
    
    @SuppressWarnings("unchecked")
    private ACLRule parseACLRule(Map<String, Object> data) {
        String name = (String) data.get("name");
        String type = (String) data.get("type");
        int priority = ((Number) data.getOrDefault("priority", 0)).intValue();
        List<String> jobs = (List<String>) data.getOrDefault("jobs", new ArrayList<>());
        List<String> environments = (List<String>) data.getOrDefault("environments", new ArrayList<>());
        List<String> envCategories = (List<String>) data.getOrDefault("envCategories", new ArrayList<>());
        List<String> users = (List<String>) data.getOrDefault("users", new ArrayList<>());
        List<String> groups = (List<String>) data.getOrDefault("groups", new ArrayList<>());
        
        return new ACLRule(name, type, priority, jobs, environments, envCategories, users, groups);
    }
    
    public FormValidation doCheckYamlConfiguration(@QueryParameter String value) {
        if (value == null || value.trim().isEmpty()) {
            return FormValidation.warning("YAML configuration is empty");
        }
        
        try {
            parseYamlConfiguration(value);
            return FormValidation.ok("YAML configuration is valid");
        } catch (Exception e) {
            return FormValidation.error("Invalid YAML: " + e.getMessage());
        }
    }
    
    // API methods for other components
    public List<String> getAllEnvironments() {
        return environmentGroups.stream()
                .flatMap(group -> group.getEnvironments().stream())
                .distinct()
                .collect(Collectors.toList());
    }
    
    public List<String> getAllEnvironmentGroups() {
        return environmentGroups.stream()
                .map(EnvironmentGroup::getName)
                .collect(Collectors.toList());
    }
    
    @CheckForNull
    public EnvironmentGroup getEnvironmentGroupByName(String name) {
        return environmentGroups.stream()
                .filter(group -> group.getName().equals(name))
                .findFirst()
                .orElse(null);
    }
    
    @CheckForNull
    public EnvironmentGroup getEnvironmentGroupForEnvironment(String environment) {
        return environmentGroups.stream()
                .filter(group -> group.getEnvironments().contains(environment))
                .findFirst()
                .orElse(null);
    }
    
    public List<String> getSshKeysForEnvironment(String environment) {
        EnvironmentGroup group = getEnvironmentGroupForEnvironment(environment);
        return group != null ? new ArrayList<>(group.getSshKeyIds()) : new ArrayList<>();
    }
    
    public List<String> getVaultKeysForEnvironment(String environment) {
        EnvironmentGroup group = getEnvironmentGroupForEnvironment(environment);
        return group != null ? new ArrayList<>(group.getVaultKeyIds()) : new ArrayList<>();
    }
    
    @Extension
    public static class EnvironmentACLManagementLink extends ManagementLink {
        
        @Override
        public String getIconFileName() {
            return "symbol-folder-outline";
        }
        
        @Override
        public String getDisplayName() {
            return "Environment ACL Manager";
        }
        
        @Override
        public String getUrlName() {
            return "environment-acl";
        }
        
        @Override
        public String getDescription() {
            return "Manage environment groups and access control rules";
        }
        
        @Override
        public Permission getRequiredPermission() {
            return Jenkins.ADMINISTER;
        }
    }
}