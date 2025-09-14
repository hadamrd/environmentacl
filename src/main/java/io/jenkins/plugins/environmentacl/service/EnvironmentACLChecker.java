package io.jenkins.plugins.environmentacl.service;

import io.jenkins.plugins.environmentacl.EnvironmentACLGlobalConfiguration;
import io.jenkins.plugins.environmentacl.model.ACLRule;
import io.jenkins.plugins.environmentacl.model.EnvironmentGroup;

import java.util.List;
import java.util.stream.Collectors;

public class EnvironmentACLChecker {
    private final EnvironmentACLGlobalConfiguration config;

    public EnvironmentACLChecker() {
        this.config = EnvironmentACLGlobalConfiguration.get();
    }

    public boolean hasAccess(String userId, List<String> userGroups, String jobName, String environment) {
        List<ACLRule> rules = config.getAclRules();
        
        // Sort by priority (higher first)
        rules.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

        // Check deny rules first
        for (ACLRule rule : rules) {
            if ("deny".equalsIgnoreCase(rule.getType()) && matchesRule(rule, userId, userGroups, jobName, environment)) {
                return false;
            }
        }

        // Check allow rules
        for (ACLRule rule : rules) {
            if ("allow".equalsIgnoreCase(rule.getType()) && matchesRule(rule, userId, userGroups, jobName, environment)) {
                return true;
            }
        }

        return false; // Default deny
    }

    private boolean matchesRule(ACLRule rule, String userId, List<String> userGroups, String jobName, String environment) {
        return matchesJob(rule, jobName) && 
               matchesEnvironment(rule, environment) && 
               matchesUserOrGroup(rule, userId, userGroups);
    }

    private boolean matchesJob(ACLRule rule, String jobName) {
        return rule.getJobs().contains("*") || rule.getJobs().contains(jobName);
    }

    private boolean matchesEnvironment(ACLRule rule, String environment) {
        // Direct environment match
        if (rule.getEnvironments().contains("*") || rule.getEnvironments().contains(environment)) {
            return true;
        }

        // Environment group match
        if (!rule.getEnvironmentGroups().isEmpty()) {
            EnvironmentGroup group = config.getEnvironmentGroupForEnvironment(environment);
            if (group != null && (rule.getEnvironmentGroups().contains("*") || 
                                 rule.getEnvironmentGroups().contains(group.getName()))) {
                return true;
            }
        }

        // TODO: Add environment tags logic when implemented
        
        return false;
    }

    private boolean matchesUserOrGroup(ACLRule rule, String userId, List<String> userGroups) {
        // Check user match
        if (rule.getUsers().contains(userId) || rule.getUsers().contains("*")) {
            return true;
        }

        // Check group match
        return rule.getGroups().stream().anyMatch(group -> 
            "*".equals(group) || userGroups.contains(group));
    }

    public List<String> getAccessibleEnvironments(String userId, List<String> userGroups, String jobName) {
        List<String> allEnvironments = config.getAllEnvironments();
        return allEnvironments.stream()
                .filter(env -> hasAccess(userId, userGroups, jobName, env))
                .collect(Collectors.toList());
    }

    public List<String> getAccessibleEnvironmentGroups(String userId, List<String> userGroups, String jobName) {
        return config.getEnvironmentGroups().stream()
                .filter(group -> group.getEnvironments().stream()
                        .anyMatch(env -> hasAccess(userId, userGroups, jobName, env)))
                .map(group -> group.getName())
                .collect(Collectors.toList());
    }
}