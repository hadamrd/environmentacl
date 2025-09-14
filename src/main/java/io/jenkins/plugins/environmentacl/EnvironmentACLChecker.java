package io.jenkins.plugins.environmentacl;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility class for checking permissions against ACL rules
 */
public class EnvironmentACLChecker {
    
    private final EnvironmentACLGlobalConfiguration config;
    
    public EnvironmentACLChecker() {
        this.config = EnvironmentACLGlobalConfiguration.get();
    }
    
    /**
     * Check if a user has access to a specific job and environment
     */
    public boolean hasAccess(String userId, List<String> userGroups, String jobName, String environment) {
        List<ACLRule> rules = config.getAclRules();
        
        // Sort rules by priority (higher priority first)
        rules.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        
        // Check for explicit deny rules first
        for (ACLRule rule : rules) {
            if (rule.isDeny() && matchesRule(rule, userId, userGroups, jobName, environment)) {
                return false;
            }
        }
        
        // Check for allow rules
        for (ACLRule rule : rules) {
            if (rule.isAllow() && matchesRule(rule, userId, userGroups, jobName, environment)) {
                return true;
            }
        }
        
        return false; // Default deny
    }
    
    /**
     * Get list of environments accessible to a user for a specific job
     */
    public List<String> getAccessibleEnvironments(String userId, List<String> userGroups, String jobName) {
        List<String> allEnvironments = config.getAllEnvironments();
        return allEnvironments.stream()
                .filter(env -> hasAccess(userId, userGroups, jobName, env))
                .collect(Collectors.toList());
    }
    
    private boolean matchesRule(ACLRule rule, String userId, List<String> userGroups, String jobName, String environment) {
        // Check job match
        if (!rule.getJobs().contains("*") && !rule.getJobs().contains(jobName)) {
            return false;
        }
        
        // Check environment match
        if (!matchesEnvironment(rule, environment)) {
            return false;
        }
        
        // Check user/group match
        return matchesUserOrGroup(rule, userId, userGroups);
    }
    
    private boolean matchesEnvironment(ACLRule rule, String environment) {
        // Direct environment match
        if (rule.getEnvironments().contains("*") || rule.getEnvironments().contains(environment)) {
            return true;
        }
        
        // Category match
        if (!rule.getEnvCategories().isEmpty()) {
            EnvironmentGroup group = config.getEnvironmentGroupForEnvironment(environment);
            if (group != null) {
                return rule.getEnvCategories().contains("*") || 
                       rule.getEnvCategories().contains(group.getName());
            }
        }
        
        return false;
    }
    
    private boolean matchesUserOrGroup(ACLRule rule, String userId, List<String> userGroups) {
        // Check user match
        if (rule.getUsers().contains(userId)) {
            return true;
        }
        
        // Check group match
        return rule.getGroups().stream().anyMatch(userGroups::contains);
    }
}