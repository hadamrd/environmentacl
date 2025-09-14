package io.jenkins.plugins.environmentacl.step;

import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.User;
import hudson.model.Cause;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import io.jenkins.plugins.environmentacl.service.EnvironmentACLChecker;
import io.jenkins.plugins.environmentacl.service.CredentialService;
import io.jenkins.plugins.environmentacl.model.EnvironmentGroup;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class EnvironmentACLStepExecution extends SynchronousNonBlockingStepExecution<Map<String, Object>> {
    private static final long serialVersionUID = 1L;
    
    private final String environment;
    private final boolean failOnDeny;

    public EnvironmentACLStepExecution(EnvironmentACLStep step, StepContext context) {
        super(context);
        this.environment = step.getEnvironment();
        this.failOnDeny = step.isFailOnDeny();
    }

    @Override
    protected Map<String, Object> run() throws Exception {
        TaskListener listener = getContext().get(TaskListener.class);
        Run<?, ?> run = getContext().get(Run.class);
        
        // Get the actual user who triggered the build
        String userId = null;
        
        // Try to get user from build cause
        Cause.UserIdCause userCause = run.getCause(Cause.UserIdCause.class);
        userId = userCause.getUserId();
        
        // Get user object and impersonate to get groups
        User user = Jenkins.get().getUser(userId);
        var userGroups = user.getAuthorities();

        // If still no user, this might be a system/automated trigger
        if (userId == null || "SYSTEM".equals(userId)) {
            listener.getLogger().println("Warning: Build triggered by system, not a user. Using SYSTEM context.");
            userId = "SYSTEM";
        }
        
        String jobName = run.getParent().getFullName();
        
        listener.getLogger().println("Checking environment access:");
        listener.getLogger().println("  User: " + userId);
        listener.getLogger().println("  Groups: " + userGroups);
        listener.getLogger().println("  Job: " + jobName);
        listener.getLogger().println("  Environment: " + environment);
        
        // Check access
        EnvironmentACLChecker checker = new EnvironmentACLChecker();
        boolean hasAccess = checker.hasAccess(userId, userGroups, jobName, environment);
        
        Map<String, Object> result = new HashMap<>();
        result.put("environment", environment);
        result.put("hasAccess", hasAccess);
        result.put("user", userId);
        result.put("groups", userGroups);
        
        if (!hasAccess) {
            String message = String.format("Access denied to environment '%s' for user '%s' in job '%s'", 
                                          environment, userId, jobName);
            listener.getLogger().println("❌ " + message);
            
            if (failOnDeny) {
                throw new SecurityException(message);
            }
            
            result.put("errorMessage", message);
            return result;
        }
        
        listener.getLogger().println("✅ Access granted to environment: " + environment);
        
        // Get credentials
        CredentialService credService = new CredentialService();
        String sshCredentialId = credService.getSshCredentialForEnvironment(environment);
        EnvironmentGroup envGroup = credService.getEnvironmentGroup(environment);
        
        result.put("environmentGroup", envGroup != null ? envGroup.getName() : null);
        result.put("sshCredentialId", sshCredentialId);
        
        // Add vault credentials as a map
        if (envGroup != null) {
            Map<String, String> vaultCredentials = new HashMap<>();
            envGroup.getVaultCredentials().forEach(vault -> 
                vaultCredentials.put(vault.getVaultId(), vault.getCredentialId())
            );
            result.put("vaultCredentials", vaultCredentials);
        }
        
        listener.getLogger().println("Environment Group: " + result.get("environmentGroup"));
        listener.getLogger().println("SSH Credential ID: " + result.get("sshCredentialId"));
        
        return result;
    }
}