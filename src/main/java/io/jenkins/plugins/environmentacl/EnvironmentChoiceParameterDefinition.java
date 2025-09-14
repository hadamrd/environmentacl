package io.jenkins.plugins.environmentacl;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.StringParameterValue;
import hudson.model.User;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.environmentacl.model.EnvironmentACLConfig.EnvironmentGroupConfig;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

/** Custom parameter definition that shows only environments accessible to the current user */
public class EnvironmentChoiceParameterDefinition extends ParameterDefinition {

    private static final Logger LOGGER = Logger.getLogger(EnvironmentChoiceParameterDefinition.class.getName());

    private final String defaultValue;
    private final boolean showOnlyAccessible;
    private final String environmentGroupFilter;

    @DataBoundConstructor
    public EnvironmentChoiceParameterDefinition(
            String name,
            String description,
            String defaultValue,
            boolean showOnlyAccessible,
            String environmentGroupFilter) {
        super(name, description);
        this.defaultValue = defaultValue;
        this.showOnlyAccessible = showOnlyAccessible;
        this.environmentGroupFilter = environmentGroupFilter;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public boolean isShowOnlyAccessible() {
        return showOnlyAccessible;
    }

    public String getEnvironmentGroupFilter() {
        return environmentGroupFilter;
    }

    @Override
    public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
        StringParameterValue value = req.bindJSON(StringParameterValue.class, jo);
        value.setDescription(getDescription());
        return value;
    }

    public ParameterValue createValue(String str) {
        return new StringParameterValue(getName(), str, getDescription());
    }

    public ParameterValue createValue(StaplerRequest req) {
        String value = req.getParameter(getName());
        return new StringParameterValue(getName(), value, getDescription());
    }

    public ParameterValue getDefaultParameterValue() {
        return new StringParameterValue(getName(), defaultValue, getDescription());
    }

    public ListBoxModel doFillValueItems(@QueryParameter String jobName) {
        ListBoxModel items = new ListBoxModel();

        try {
            User currentUser = User.current();
            if (currentUser == null) {
                // If no user context, show all environments
                addAllEnvironments(items);
                return items;
            }

            String userId = currentUser.getId();
            List<String> userGroups = getUserGroups(currentUser);

            // Get job context if available
            String contextJobName = jobName;
            if (contextJobName == null || contextJobName.isEmpty()) {
                // Try to get job name from request context
                contextJobName = getCurrentJobName();
            }

            if (showOnlyAccessible && contextJobName != null) {
                // Filter environments based on ACL
                EnvironmentACLChecker checker = new EnvironmentACLChecker();
                List<String> accessibleEnvs = checker.getAccessibleEnvironments(userId, userGroups, contextJobName);

                // Apply group filter if specified
                List<String> filteredEnvs = applyGroupFilter(accessibleEnvs);

                for (String env : filteredEnvs) {
                    items.add(env, env);
                }
            } else {
                // Show all environments (or filtered by group)
                addAllEnvironments(items);
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error populating environment choices: {0}", e.getMessage());
            addAllEnvironments(items); // Fallback to all environments
        }

        return items;
    }

    private void addAllEnvironments(ListBoxModel items) {
        EnvironmentACLGlobalConfiguration config = EnvironmentACLGlobalConfiguration.get();
        List<String> environments = applyGroupFilter(config.getAllEnvironments());
        for (String env : environments) {
            items.add(env, env);
        }
    }

    private List<String> applyGroupFilter(List<String> environments) {
        if (environmentGroupFilter == null || environmentGroupFilter.trim().isEmpty()) {
            return environments;
        }

        EnvironmentACLGlobalConfiguration config = EnvironmentACLGlobalConfiguration.get();
        EnvironmentGroupConfig group = config.getEnvironmentGroupByName(environmentGroupFilter);

        if (group != null) {
            // Filter to only environments in the specified group
            return environments.stream()
                    .filter(env -> group.environments.contains(env))
                    .collect(Collectors.toList());
        }

        return environments;
    }

    private List<String> getUserGroups(User user) {
        List<String> groups = new ArrayList<>();

        try {
            // Try to get groups from security realm
            Jenkins jenkins = Jenkins.get();
            if (jenkins.getSecurityRealm() != null) {
                // This is a simplified approach - you might need to adapt based on your security setup
                hudson.security.SecurityRealm realm = jenkins.getSecurityRealm();
                // Add logic to extract groups based on your security realm
            }

            // Fallback: try to get groups from user properties or external systems
            // This is where you'd integrate with LDAP, AD, or other group providers

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not retrieve user groups for {0}: {1}", new Object[]{user.getId(), e.getMessage()});
        }

        return groups;
    }

    private String getCurrentJobName() {
        try {
            // Try to get job name from current request context
            StaplerRequest currentRequest = org.kohsuke.stapler.Stapler.getCurrentRequest();
            if (currentRequest != null) {
                String referer = currentRequest.getHeader("Referer");
                if (referer != null) {
                    // Extract job name from referer URL
                    // This is a simple approach - you might need more sophisticated parsing
                    String[] parts = referer.split("/job/");
                    if (parts.length > 1) {
                        String jobPart = parts[1];
                        int nextSlash = jobPart.indexOf('/');
                        if (nextSlash > 0) {
                            return jobPart.substring(0, nextSlash);
                        }
                        return jobPart;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Could not determine job name from context: {0}", e.getMessage());
        }
        return null;
    }

    @Extension
    @Symbol("environmentChoice")
    public static class DescriptorImpl extends ParameterDescriptor {

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Environment Choice Parameter";
        }

        @Override
        public String getHelpFile() {
            return "/plugin/environment-acl-manager/help-environmentChoice.html";
        }

        public ListBoxModel doFillEnvironmentGroupFilterItems() {
            ListBoxModel items = new ListBoxModel();
            items.add("-- All Groups --", "");

            EnvironmentACLGlobalConfiguration config = EnvironmentACLGlobalConfiguration.get();
            for (String groupName : config.getAllEnvironmentGroups()) {
                items.add(groupName, groupName);
            }

            return items;
        }
    }
}
