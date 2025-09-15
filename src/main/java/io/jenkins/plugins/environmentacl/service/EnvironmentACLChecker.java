package io.jenkins.plugins.environmentacl.service;

import io.jenkins.plugins.environmentacl.EnvironmentACLGlobalConfiguration;
import io.jenkins.plugins.environmentacl.model.ACLRule;
import io.jenkins.plugins.environmentacl.model.EnvironmentGroup;
import io.jenkins.plugins.environmentacl.service.UserContextHelper.UserContext;
import hudson.model.Run;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class EnvironmentACLChecker {
    
    private EnvironmentACLChecker() {}

    public static boolean hasAccess(String userId, List<String> userGroups, String jobName, String environment) {
        EnvironmentACLGlobalConfiguration config = EnvironmentACLGlobalConfiguration.get();
        List<ACLRule> rules = config.getAclRules();

        // Sort by priority (higher first)
        rules.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

        // Check deny rules first
        for (ACLRule rule : rules) {
            if ("deny".equalsIgnoreCase(rule.getType())
                    && matchesRule(rule, userId, userGroups, jobName, environment)) {
                return false;
            }
        }

        // Check allow rules
        for (ACLRule rule : rules) {
            if ("allow".equalsIgnoreCase(rule.getType())
                    && matchesRule(rule, userId, userGroups, jobName, environment)) {
                return true;
            }
        }

        return false; // Default deny
    }

    /**
     * Check access using current authentication context (for UI/parameters)
     */
    public static boolean hasAccess(String jobName, String environment) {
        UserContext context = UserContextHelper.getCurrentUserContext();
        return hasAccess(context.getUserId(), context.getGroups(), jobName, environment);
    }
    
    /**
     * Check access using build run context (for pipeline steps)
     */
    public static boolean hasAccess(Run<?, ?> run, String environment) {
        UserContext context = UserContextHelper.getUserContextFromRun(run);
        String jobName = run.getParent().getFullName();
        return hasAccess(context.getUserId(), context.getGroups(), jobName, environment);
    }

    private static boolean matchesRule(
            ACLRule rule, String userId, List<String> userGroups, String jobName, String environment) {
        return matchesJob(rule, jobName)
                && matchesEnvironment(rule, environment)
                && matchesUserOrGroup(rule, userId, userGroups);
    }

    private static boolean matchesJob(ACLRule rule, String jobName) {
        return rule.getJobs().stream().anyMatch(jobPattern -> {
            if ("*".equals(jobPattern)) {
                return true;
            }
            try {
                Pattern pattern = Pattern.compile(jobPattern);
                return pattern.matcher(jobName).matches();
            } catch (Exception e) {
                return jobPattern.equals(jobName);
            }
        });
    }

    private static boolean matchesEnvironment(ACLRule rule, String environment) {
        EnvironmentACLGlobalConfiguration config = EnvironmentACLGlobalConfiguration.get();
        
        // Direct environment match
        if (rule.getEnvironments().contains("*") || rule.getEnvironments().contains(environment)) {
            return true;
        }

        // Environment group match
        if (!rule.getEnvironmentGroups().isEmpty()) {
            EnvironmentGroup group = config.getEnvironmentGroupForEnvironment(environment);
            if (group != null
                    && (rule.getEnvironmentGroups().contains("*")
                            || rule.getEnvironmentGroups().contains(group.getName()))) {
                return true;
            }
        }

        // Environment tags match
        if (!rule.getEnvironmentTags().isEmpty()) {
            EnvironmentGroup group = config.getEnvironmentGroupForEnvironment(environment);
            if (group != null && group.getTags() != null) {
                boolean tagMatch = rule.getEnvironmentTags().stream()
                    .anyMatch(ruleTag -> "*".equals(ruleTag) || group.getTags().contains(ruleTag));
                if (tagMatch) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean matchesUserOrGroup(ACLRule rule, String userId, List<String> userGroups) {
        if (rule.getUsers().contains(userId) || rule.getUsers().contains("*")) {
            return true;
        }
        return rule.getGroups().stream().anyMatch(group -> "*".equals(group) || userGroups.contains(group));
    }

    public static List<String> getAccessibleEnvironments(String jobName) {
        UserContext context = UserContextHelper.getCurrentUserContext();
        EnvironmentACLGlobalConfiguration config = EnvironmentACLGlobalConfiguration.get();
        List<String> allEnvironments = config.getAllEnvironments();
        
        return allEnvironments.stream()
                .filter(env -> hasAccess(context.getUserId(), context.getGroups(), jobName, env))
                .collect(Collectors.toList());
    }

    public static List<String> getAccessibleEnvironmentGroups(String userId, List<String> userGroups, String jobName) {
        EnvironmentACLGlobalConfiguration config = EnvironmentACLGlobalConfiguration.get();
        return config.getEnvironmentGroups().stream()
                .filter(group ->
                        group.getEnvironments().stream().anyMatch(env -> hasAccess(userId, userGroups, jobName, env)))
                .map(group -> group.getName())
                .collect(Collectors.toList());
    }
}