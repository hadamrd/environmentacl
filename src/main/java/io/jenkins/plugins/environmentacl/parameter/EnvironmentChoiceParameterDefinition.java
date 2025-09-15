package io.jenkins.plugins.environmentacl.parameter;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.StringParameterValue;
import hudson.model.User;
import io.jenkins.plugins.environmentacl.service.EnvironmentACLChecker;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.springframework.security.core.Authentication;

public class EnvironmentChoiceParameterDefinition extends ParameterDefinition {

    @DataBoundConstructor
    public EnvironmentChoiceParameterDefinition(String name, String description) {
        super(name);
        setDescription(description);
    }

    public List<String> getChoices() {
        debugRequest();
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
            System.out.println("getChoices() returning: " + accessibleEnvs);

            return accessibleEnvs;

        } catch (Exception e) {
            return List.of("(Error loading environments: " + e.getMessage() + ")");
        }
    }

    // Add this to catch ANY method calls to your parameter
    public void debugRequest() {
        try {
            StaplerRequest req = org.kohsuke.stapler.Stapler.getCurrentRequest();
            if (req != null) {
                System.out.println("=== REQUEST DEBUG ===");
                System.out.println("Request URI: " + req.getRequestURI());
                System.out.println("Request Method: " + req.getMethod());

                java.util.Enumeration<String> paramNames = req.getParameterNames();
                while (paramNames.hasMoreElements()) {
                    String paramName = paramNames.nextElement();
                    String[] values = req.getParameterValues(paramName);
                    System.out.println("  " + paramName + " = " + java.util.Arrays.toString(values));
                }
            }
        } catch (Exception e) {
            System.out.println("Debug error: " + e.getMessage());
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
        String value = jo.optString("value", "");

        if ("(No environments accessible)".equals(value) || value.startsWith("(Error")) {
            value = "";
        }

        return new StringParameterValue(getName(), value, getDescription());
    }

    @Override
    public ParameterValue createValue(StaplerRequest req) {
        String[] values = req.getParameterValues(getName());
        String value = "";
        if (values != null && values.length > 0) {
            value = values[0];
            System.out.println("Raw value from request: '" + value + "'");
            if ("(No environments accessible)".equals(value) || value.startsWith("(Error")) {
                value = "";
            }
        }

        return new StringParameterValue(getName(), value, getDescription());
    }

    @Override
    public ParameterValue getDefaultParameterValue() {
        List<String> choices = getChoices();
        String defaultValue = "";
        if (!choices.isEmpty() && !choices.get(0).startsWith("(")) {
            defaultValue = choices.get(0);
        }
        return new StringParameterValue(getName(), defaultValue, getDescription());
    }

    @Extension
    @Symbol("environmentChoice")
    public static class DescriptorImpl extends ParameterDefinition.ParameterDescriptor {
        @Override
        public String getDisplayName() {
            return "Environment Choice Parameter";
        }
    }
}
