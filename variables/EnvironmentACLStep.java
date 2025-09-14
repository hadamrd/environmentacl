package io.jenkins.plugins.environmentacl;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.User;
import io.jenkins.plugins.environmentacl.model.EnvironmentACLConfig.EnvironmentGroupConfig;

/**
 * Pipeline step for checking environment access and getting environment information
 */
public class EnvironmentACLStep extends Step {
    
    private final String environment;
    private final String action;
    
    @DataBoundConstructor
    public EnvironmentACLStep(String environment, String action) {
        this.environment = environment;
        this.action = action != null ? action : "check";
    }
    
    public String getEnvironment() {
        return environment;
    }
    
    public String getAction() {
        return action;
    }
    
    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new EnvironmentACLStepExecution(this, context);
    }
    
    public static class EnvironmentACLStepExecution extends SynchronousNonBlockingStepExecution<Object> {
        
        private final EnvironmentACLStep step;
        
        public EnvironmentACLStepExecution(EnvironmentACLStep step, StepContext context) {
            super(context);
            this.step = step;
        }
        
        @Override
        protected Object run() throws Exception {
            Run<?, ?> run = getContext().get(Run.class);
            TaskListener listener = getContext().get(TaskListener.class);
            
            String jobName = run.getParent().getFullName();
            
            // Get current user
            User currentUser = User.current();
            if (currentUser == null) {
                throw new Exception("Cannot determine current user for environment access check");
            }
            
            String userId = currentUser.getId();
            List<String> userGroups = getUserGroups(currentUser);
            
            EnvironmentACLChecker checker = new EnvironmentACLChecker();
            
            switch (step.getAction().toLowerCase()) {
                case "check":
                    boolean hasAccess = checker.hasAccess(userId, userGroups, jobName, step.getEnvironment());
                    if (!hasAccess) {
                        throw new Exception("User " + userId + " does not have access to environment: " + step.getEnvironment());
                    }
                    listener.getLogger().println("✓ Access granted to environment: " + step.getEnvironment());
                    return true;
                    
                case "list":
                    List<String> accessibleEnvs = checker.getAccessibleEnvironments(userId, userGroups, jobName);
                    listener.getLogger().println("Accessible environments: " + String.join(", ", accessibleEnvs));
                    return accessibleEnvs;
                    
                case "info":
                    EnvironmentACLGlobalConfiguration config = EnvironmentACLGlobalConfiguration.get();
                    Map<String, Object> envInfo = new HashMap<>();
                    
                    envInfo.put("environment", step.getEnvironment());
                    envInfo.put("sshKeys", config.getSshKeysForEnvironment(step.getEnvironment()));
                    envInfo.put("vaultKeys", config.getVaultKeysForEnvironment(step.getEnvironment()));
                    
                    EnvironmentGroupConfig group = config.getEnvironmentGroupForEnvironment(step.getEnvironment());
                    if (group != null) {
                        envInfo.put("group", group.name);
                        envInfo.put("nodeLabels", group.nodeLabels);
                    }
                    
                    return envInfo;
                    
                default:
                    throw new Exception("Unknown action: " + step.getAction() + ". Supported actions: check, list, info");
            }
        }
        
        private List<String> getUserGroups(User user) {
            // Implementation to get user groups - adapt based on your security setup
            return Collections.emptyList();
        }
    }
    
    @Extension
    public static class DescriptorImpl extends StepDescriptor {
        
        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(Run.class, TaskListener.class);
        }
        
        @Override
        public String getFunctionName() {
            return "environmentACL";
        }
        
        @Nonnull
        @Override
        public String getDisplayName() {
            return "Environment ACL Check";
        }
    }
}

/**
 * Pipeline step to get environment credentials
 */
class GetEnvironmentCredentialsStep extends Step {
    
    private final String environment;
    private final String credentialType;
    
    @DataBoundConstructor
    public GetEnvironmentCredentialsStep(String environment, String credentialType) {
        this.environment = environment;
        this.credentialType = credentialType != null ? credentialType : "ssh";
    }
    
    public String getEnvironment() {
        return environment;
    }
    
    public String getCredentialType() {
        return credentialType;
    }
    
    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new GetEnvironmentCredentialsStepExecution(this, context);
    }
    
    public static class GetEnvironmentCredentialsStepExecution extends SynchronousNonBlockingStepExecution<List<String>> {
        
        private final GetEnvironmentCredentialsStep step;
        
        public GetEnvironmentCredentialsStepExecution(GetEnvironmentCredentialsStep step, StepContext context) {
            super(context);
            this.step = step;
        }
        
        @Override
        protected List<String> run() throws Exception {
            EnvironmentACLGlobalConfiguration config = EnvironmentACLGlobalConfiguration.get();
            
            switch (step.getCredentialType().toLowerCase()) {
                case "ssh":
                    return config.getSshKeysForEnvironment(step.getEnvironment());
                case "vault":
                    return config.getVaultKeysForEnvironment(step.getEnvironment());
                default:
                    throw new Exception("Unknown credential type: " + step.getCredentialType() + ". Supported types: ssh, vault");
            }
        }
    }
    
    @Extension
    public static class DescriptorImpl extends StepDescriptor {
        
        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(Run.class);
        }
        
        @Override
        public String getFunctionName() {
            return "getEnvironmentCredentials";
        }
        
        @Nonnull
        @Override
        public String getDisplayName() {
            return "Get Environment Credentials";
        }
    }
}