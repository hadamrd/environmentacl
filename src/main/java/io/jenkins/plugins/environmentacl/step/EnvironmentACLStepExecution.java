package io.jenkins.plugins.environmentacl.step;

import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;

import io.jenkins.plugins.environmentacl.service.EnvironmentACLChecker;
import io.jenkins.plugins.environmentacl.service.CredentialService;
import io.jenkins.plugins.environmentacl.service.UserContextHelper;
import io.jenkins.plugins.environmentacl.service.UserContextHelper.UserContext;
import io.jenkins.plugins.environmentacl.model.EnvironmentGroup;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Logger;

public class EnvironmentACLStepExecution extends SynchronousNonBlockingStepExecution<Map<String, Object>> {
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(EnvironmentACLStepExecution.class.getName());
    
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
        
        // Get user context from run (handles complexity internally)
        UserContext userContext = UserContextHelper.getUserContextFromRun(run);
        String jobName = run.getParent().getFullName();
        
        // Secure logging - NO group names in pipeline logs
        listener.getLogger().println("Checking environment access:");
        listener.getLogger().println("  User: " + userContext.getUserId());
        listener.getLogger().println("  Job: " + jobName);
        listener.getLogger().println("  Environment: " + environment);
        
        LOGGER.fine("Environment ACL check - User: " + userContext.getUserId() + 
                   ", Groups: " + userContext.getGroups() + 
                   ", Job: " + jobName + 
                   ", Environment: " + environment);
        
        // Simple access check using helper
        boolean hasAccess = EnvironmentACLChecker.hasAccess(run, environment);
        
        Map<String, Object> result = buildResult(userContext, hasAccess);
        
        if (!hasAccess) {
            String message = String.format(
                    "Access denied to environment '%s' for user '%s' in job '%s'", 
                    environment, userContext.getUserId(), jobName);
            listener.getLogger().println("❌ " + message);

            if (failOnDeny) {
                throw new SecurityException(message);
            }

            result.put("errorMessage", message);
            return result;
        }

        listener.getLogger().println("✅ Access granted to environment: " + environment);
        
        // Add credential information
        addCredentialInfo(result);
        
        listener.getLogger().println("Environment Group: " + result.get("environmentGroup"));
        listener.getLogger().println("SSH Credential ID: " + result.get("sshCredentialId"));

        return result;
    }
    
    private Map<String, Object> buildResult(UserContext userContext, boolean hasAccess) {
        Map<String, Object> result = new HashMap<>();
        result.put("environment", environment);
        result.put("hasAccess", hasAccess);
        result.put("user", userContext.getUserId());
        return result;
    }
    
    private void addCredentialInfo(Map<String, Object> result) {
        CredentialService credService = new CredentialService();
        String sshCredentialId = credService.getSshCredentialForEnvironment(environment);
        EnvironmentGroup envGroup = credService.getEnvironmentGroup(environment);

        result.put("environmentGroup", envGroup != null ? envGroup.getName() : null);
        result.put("sshCredentialId", sshCredentialId);

        // Add vault credentials as a map
        if (envGroup != null) {
            Map<String, String> vaultCredentials = new HashMap<>();
            envGroup.getVaultCredentials()
                    .forEach(vault -> vaultCredentials.put(vault.getVaultId(), vault.getCredentialId()));
            result.put("vaultCredentials", vaultCredentials);
        }
    }
}