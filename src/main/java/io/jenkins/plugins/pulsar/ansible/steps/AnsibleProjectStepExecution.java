package io.jenkins.plugins.pulsar.ansible.steps;

import hudson.AbortException;
import hudson.Launcher;
import hudson.model.TaskListener;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.jenkinsci.plugins.workflow.steps.*;

public class AnsibleProjectStepExecution extends AbstractStepExecutionImpl {
    private static final long serialVersionUID = 1L;
    
    private final String projectId;
    private final Map<String, String> version;
    private final List<String> containerOptions;
    private final boolean cleanup;
    
    private transient AnsibleContext ansibleContext;
    private transient BodyExecution body;
    
    AnsibleProjectStepExecution(AnsibleProjectStep step, StepContext context) {
        super(context);
        this.projectId = step.getProjectId();
        this.version = step.getVersion();
        this.containerOptions = step.getContainerOptions();
        this.cleanup = step.isCleanup();
    }
    
    @Override
    public boolean start() throws Exception {
        StepContext context = getContext();
        TaskListener listener = context.get(TaskListener.class);
        
        // Create Ansible context
        ansibleContext = AnsibleContext.getOrCreate(
                projectId, version, context, containerOptions);
        
        listener.getLogger().println("=== Ansible Project: " + projectId + " ===");
        listener.getLogger().println("Version: " + version);
        listener.getLogger().println("Project Root: " + ansibleContext.getProjectRoot());
        
        // Start body execution with callback
        body = context.newBodyInvoker()
                .withContext(ansibleContext)
                .withContext(ansibleContext.getContainer())
                .withCallback(new BodyCallback())
                .start();
        
        return false; // Async execution
    }
    
    @Override
    public void stop(Throwable cause) throws Exception {
        if (body != null) {
            body.cancel(cause);
        }
        cleanup();
    }
    
    private void cleanup() {
        try {
            if (ansibleContext != null && cleanup) {
                StepContext context = getContext();
                Launcher launcher = context.get(Launcher.class);
                TaskListener listener = context.get(TaskListener.class);
                ansibleContext.release(cleanup, launcher, listener);
            }
        } catch (Exception e) {
            // Log error but don't propagate
            try {
                TaskListener listener = getContext().get(TaskListener.class);
                listener.error("Error during cleanup: " + e.getMessage());
            } catch (Exception ignored) {
                // If we can't even get the listener, just move on
            }
        }
    }
    
    private class BodyCallback extends BodyExecutionCallback {
        @Override
        public void onSuccess(StepContext context, Object result) {
            cleanup();
            
            // Check if the body returned a non-zero exit code
            if (result instanceof Integer) {
                Integer exitCode = (Integer) result;
                if (exitCode != 0) {
                    // Body execution returned error code - treat as failure
                    String errorMsg = "Ansible execution failed with exit code: " + exitCode;
                    try {
                        TaskListener listener = getContext().get(TaskListener.class);
                        listener.error(errorMsg);
                    } catch (Exception ignored) {}
                    
                    getContext().onFailure(new AbortException(errorMsg));
                    return;
                }
            }
            
            // Success case - either null result or exit code 0
            getContext().onSuccess(result);
        }
        
        @Override
        public void onFailure(StepContext context, Throwable t) {
            cleanup();
            
            // Extract simple message from the throwable
            String message = t.getMessage();
            if (message == null || message.isEmpty()) {
                message = "Ansible execution failed: " + t.getClass().getSimpleName();
            }
            
            // Log the error
            try {
                TaskListener listener = getContext().get(TaskListener.class);
                listener.error("Ansible project execution failed: " + message);
                
                // If it's an ExecutionException, try to get the cause
                if (t instanceof ExecutionException && t.getCause() != null) {
                    Throwable cause = t.getCause();
                    listener.error("Caused by: " + cause.getMessage());
                }
            } catch (Exception ignored) {}
            
            // Always convert to AbortException to avoid serialization issues
            getContext().onFailure(new AbortException(message));
        }
    }
}