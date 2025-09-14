package io.jenkins.plugins.environmentacl;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.Extension;
import io.jenkins.plugins.environmentacl.model.EnvironmentACLConfig.ACLRuleConfig;
import io.jenkins.plugins.environmentacl.model.EnvironmentACLConfig.EnvironmentGroupConfig;
import jenkins.model.GlobalConfiguration;

/**
 * Minimal GlobalConfiguration ONLY for JCasC support.
 * All UI configuration is handled by EnvironmentACLManagementLink.
 * This class just provides the JCasC entry point and data storage.
 */
@Extension
@Symbol("environmentACL")
public class EnvironmentACLGlobalConfiguration extends GlobalConfiguration {

    private static final Logger LOGGER = Logger.getLogger(EnvironmentACLGlobalConfiguration.class.getName());

    // Data storage - shared with ManagementLink
    private List<EnvironmentGroupConfig> environmentGroups = new ArrayList<>();
    private List<ACLRuleConfig> rules = new ArrayList<>();

    public EnvironmentACLGlobalConfiguration() {
        load();
    }

    public static EnvironmentACLGlobalConfiguration get() {
        return GlobalConfiguration.all().get(EnvironmentACLGlobalConfiguration.class);
    }

    // ========== Simple Getters/Setters for JCasC ==========
    
    public List<EnvironmentGroupConfig> getEnvironmentGroups() {
        return environmentGroups != null ? environmentGroups : new ArrayList<>();
    }

    @DataBoundSetter
    public void setEnvironmentGroups(List<EnvironmentGroupConfig> environmentGroups) {
        this.environmentGroups = environmentGroups != null ? environmentGroups : new ArrayList<>();
        save();
    }

    public List<ACLRuleConfig> getRules() {
        return rules != null ? rules : new ArrayList<>();
    }
    
    @DataBoundSetter
    public void setRules(List<ACLRuleConfig> rules) {
        this.rules = rules != null ? rules : new ArrayList<>();
        save();
    }
    
    // ========== NO UI METHODS - No configure(), no Jelly files needed! ==========
    // The UI is entirely handled by EnvironmentACLManagementLink
    
    // ========== Helper Methods (used by other components) ==========
    
    public List<String> getAllEnvironments() {
        return getEnvironmentGroups().stream()
                .flatMap(group -> group.environments.stream())
                .distinct()
                .collect(Collectors.toList());
    }

    public List<String> getAllEnvironmentGroups() {
        return getEnvironmentGroups().stream()
                .map(group -> group.name)
                .collect(Collectors.toList());
    }

    public EnvironmentGroupConfig getEnvironmentGroupByName(String name) {
        return getEnvironmentGroups().stream()
                .filter(group -> group.name.equals(name))
                .findFirst()
                .orElse(null);
    }

    public EnvironmentGroupConfig getEnvironmentGroupForEnvironment(String environment) {
        return getEnvironmentGroups().stream()
                .filter(group -> group.environments.contains(environment))
                .findFirst()
                .orElse(null);
    }

    public List<String> getSshKeysForEnvironment(String environment) {
        EnvironmentGroupConfig group = getEnvironmentGroupForEnvironment(environment);
        return group != null ? new ArrayList<>(group.sshKeys) : new ArrayList<>();
    }

    public List<String> getVaultKeysForEnvironment(String environment) {
        EnvironmentGroupConfig group = getEnvironmentGroupForEnvironment(environment);
        return group != null ? new ArrayList<>(group.vaultKeys) : new ArrayList<>();
    }

    public List<String> getNodeLabelsForEnvironment(String environment) {
        EnvironmentGroupConfig group = getEnvironmentGroupForEnvironment(environment);
        return group != null ? new ArrayList<>(group.nodeLabels) : new ArrayList<>();
    }
    
    // Compatibility
    public List<ACLRuleConfig> getAclRules() {
        return getRules();
    }
}