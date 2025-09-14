package io.jenkins.plugins.environmentacl.parameter;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.User;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import io.jenkins.plugins.environmentacl.service.EnvironmentACLChecker;

import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class EnvironmentChoiceParameterDefinition extends ParameterDefinition {
    private final String jobName;

    @DataBoundConstructor
    public EnvironmentChoiceParameterDefinition(String name, String description) {
        super(name);
        setDescription(description);
        this.jobName = null; // Will be set when used in context
    }

    public List<String> getChoices() {
        try {
            // Get current user
            Authentication auth = Jenkins.getAuthentication2();
            String userId = auth.getName();
            List<String> userGroups = new ArrayList<>();

            // Try to get actual user groups
            if (!"SYSTEM".equals(userId) && !"anonymous".equals(userId)) {
                User user = Jenkins.get().getUser(userId);
                userGroups = user.getAuthorities();
            }

            // Get accessible environments
            EnvironmentACLChecker checker = new EnvironmentACLChecker();
            String currentJobName = getCurrentJobName();
            
            List<String> accessibleEnvs = checker.getAccessibleEnvironments(userId, userGroups, currentJobName);
            
            if (accessibleEnvs.isEmpty()) {
                return List.of("(No environments accessible)");
            }
            
            return accessibleEnvs;
            
        } catch (Exception e) {
            return List.of("(Error loading environments: " + e.getMessage() + ")");
        }
    }

    private String getCurrentJobName() {
        try {
            // Try to get job name from current request context
            StaplerRequest currentRequest = org.kohsuke.stapler.Stapler.getCurrentRequest();
            if (currentRequest != null) {
                String uri = currentRequest.getRequestURI();
                if (uri.contains("/job/")) {
                    String[] parts = uri.split("/job/");
                    if (parts.length > 1) {
                        return parts[1].split("/")[0];
                    }
                }
            }
            return "*"; // Fallback to wildcard
        } catch (Exception e) {
            return "*";
        }
    }

    @Override
    public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
        String value = jo.getString("value");
        if ("(No environments accessible)".equals(value) || value.startsWith("(Error")) {
            value = ""; // Convert display message to empty value
        }
        return new EnvironmentChoiceParameterValue(getName(), value, getDescription());
    }

    @Override
    public ParameterValue createValue(StaplerRequest req) {
        String[] values = req.getParameterValues(getName());
        if (values == null || values.length == 0) {
            return new EnvironmentChoiceParameterValue(getName(), "", getDescription());
        }
        String value = values[0];
        if ("(No environments accessible)".equals(value) || value.startsWith("(Error")) {
            value = "";
        }
        return new EnvironmentChoiceParameterValue(getName(), value, getDescription());
    }

    @Override
    public ParameterValue getDefaultParameterValue() {
        List<String> choices = getChoices();
        String defaultValue = choices.isEmpty() ? "" : 
                             choices.get(0).startsWith("(") ? "" : choices.get(0);
        return new EnvironmentChoiceParameterValue(getName(), defaultValue, getDescription());
    }

    @Extension
    @Symbol("environmentChoice")
    public static class DescriptorImpl extends ParameterDescriptor {
        @Override
        public String getDisplayName() {
            return "Environment Choice Parameter";
        }
    }
}