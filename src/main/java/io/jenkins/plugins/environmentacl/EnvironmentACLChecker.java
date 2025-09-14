package io.jenkins.plugins.environmentacl;

import io.jenkins.plugins.environmentacl.model.EnvironmentACLConfig.ACLRuleConfig;
import io.jenkins.plugins.environmentacl.model.EnvironmentACLConfig.EnvironmentGroupConfig;
import java.util.List;
import java.util.stream.Collectors;

/** Utility class for checking permissions against ACL rules using Jackson POJOs directly */
public class EnvironmentACLChecker {

    private final EnvironmentACLGlobalConfiguration config;

    public EnvironmentACLChecker() {
        this.config = EnvironmentACLGlobalConfiguration.get();
    }

    /** Check if a user has access to a specific job and environment */
    public boolean hasAccess(String userId, List<String> userGroups, String jobName, String environment) {
        List<ACLRuleConfig> rules = config.getAclRules();

        // Sort rules by priority (higher priority first)
        rules.sort((a, b) -> Integer.compare(b.priority, a.priority));

        // Check for explicit deny rules first
        for (ACLRuleConfig rule : rules) {
            if ("deny".equalsIgnoreCase(rule.type) && matchesRule(rule, userId, userGroups, jobName, environment)) {
                return false;
            }
        }

        // Check for allow rules
        for (ACLRuleConfig rule : rules) {
            if ("allow".equalsIgnoreCase(rule.type) && matchesRule(rule, userId, userGroups, jobName, environment)) {
                return true;
            }
        }

        return false; // Default deny
    }

    /** Get list of environments accessible to a user for a specific job */
    public List<String> getAccessibleEnvironments(String userId, List<String> userGroups, String jobName) {
        List<String> allEnvironments = config.getAllEnvironments();
        return allEnvironments.stream()
                .filter(env -> hasAccess(userId, userGroups, jobName, env))
                .collect(Collectors.toList());
    }

    private boolean matchesRule(
            ACLRuleConfig rule, String userId, List<String> userGroups, String jobName, String environment) {
        // Check job match
        if (!rule.jobs.contains("*") && !rule.jobs.contains(jobName)) {
            return false;
        }

        // Check environment match
        if (!matchesEnvironment(rule, environment)) {
            return false;
        }

        // Check user/group match
        return matchesUserOrGroup(rule, userId, userGroups);
    }

    private boolean matchesEnvironment(ACLRuleConfig rule, String environment) {
        // Direct environment match
        if (rule.environments.contains("*") || rule.environments.contains(environment)) {
            return true;
        }

        // Category match
        if (!rule.envCategories.isEmpty()) {
            EnvironmentGroupConfig group = config.getEnvironmentGroupForEnvironment(environment);
            if (group != null) {
                return rule.envCategories.contains("*") || rule.envCategories.contains(group.name);
            }
        }

        return false;
    }

    private boolean matchesUserOrGroup(ACLRuleConfig rule, String userId, List<String> userGroups) {
        // Check user match
        if (rule.users.contains(userId)) {
            return true;
        }

        // Check group match
        return rule.groups.stream().anyMatch(userGroups::contains);
    }
}
