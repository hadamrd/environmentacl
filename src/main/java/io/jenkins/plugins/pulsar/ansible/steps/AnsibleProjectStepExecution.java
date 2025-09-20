package io.jenkins.plugins.pulsar.ansible.steps;

import hudson.AbortException;
import hudson.Launcher;
import hudson.model.TaskListener;
import java.util.List;
import java.util.Map;
import org.jenkinsci.plugins.workflow.steps.*;

public class AnsibleProjectStepExecution extends SynchronousNonBlockingStepExecution<Void> {
    private static final long serialVersionUID = 1L;

    private final String projectId;
    private final Map<String, String> version;
    private final List<String> containerOptions;
    private final boolean cleanup;

    AnsibleProjectStepExecution(AnsibleProjectStep step, StepContext context) {
        super(context);
        this.projectId = step.getProjectId();
        this.version = step.getVersion();
        this.containerOptions = step.getContainerOptions();
        this.cleanup = step.isCleanup();
    }

    @Override
    protected Void run() throws Exception {
        StepContext context = getContext();
        TaskListener listener = context.get(TaskListener.class);
        Launcher launcher = context.get(Launcher.class);
        
        AnsibleContext ansibleContext = null;
        
        try {
            // Create Ansible context
            ansibleContext = AnsibleContext.getOrCreate(projectId, version, context, containerOptions);

            listener.getLogger().println("=== Ansible Project: " + projectId + " ===");
            listener.getLogger().println("Version: " + version);
            listener.getLogger().println("Project Root: " + ansibleContext.getProjectRoot());

            // Execute the body synchronously
            context.newBodyInvoker()
                    .withContext(ansibleContext)
                    .withContext(ansibleContext.getContainer())
                    .start()
                    .get(); // Synchronous execution

            listener.getLogger().println("Ansible project execution completed successfully");
            return null;

        } catch (Exception e) {
            // Simple error handling - always wrap in AbortException
            String message = e.getMessage();
            if (message == null || message.trim().isEmpty()) {
                message = "Ansible execution failed: " + e.getClass().getSimpleName();
            }
            
            listener.error("Ansible project execution failed: " + message);
            throw new AbortException(message);
            
        } finally {
            // Always cleanup
            if (ansibleContext != null && cleanup) {
                try {
                    ansibleContext.release(cleanup, launcher, listener);
                } catch (Exception cleanupError) {
                    listener.error("Warning: Cleanup failed: " + cleanupError.getMessage());
                    // Don't throw - cleanup errors shouldn't fail the build
                }
            }
        }
    }
}