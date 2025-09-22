package io.jenkins.plugins.pulsar.ansible.steps;

import hudson.Launcher;
import hudson.model.TaskListener;
import java.util.List;
import org.jenkinsci.plugins.workflow.steps.*;

public class AnsibleProjectStepExecution extends SynchronousNonBlockingStepExecution<Void> {
    private static final long serialVersionUID = 1L;

    private final String projectId;
    private final String ref;
    private final List<String> containerOptions;
    private final boolean cleanup;

    AnsibleProjectStepExecution(AnsibleProjectStep step, StepContext context) {
        super(context);
        this.projectId = step.getProjectId();
        this.ref = step.getRef();
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
            ansibleContext = AnsibleContext.getOrCreate(projectId, ref, context, containerOptions);

            listener.getLogger().println("=== Ansible Project: " + projectId + " ===");
            listener.getLogger().println("Version: " + ref);
            listener.getLogger().println("Project Root: " + ansibleContext.getProjectDir());

            // Execute the body synchronously
            context.newBodyInvoker()
                    .withContext(ansibleContext)
                    .withContext(ansibleContext.getExecEnv())
                    .start()
                    .get();

            listener.getLogger().println("Ansible project execution completed successfully");
            return null;

        } finally {
            // ALWAYS release reference - the cleanup flag controls cleanup behavior
            if (ansibleContext != null) {
                try {
                    ansibleContext.release(cleanup, launcher, listener);
                } catch (Exception cleanupError) {
                    listener.error("Warning: Release failed: " + cleanupError.getMessage());
                    // Don't throw - cleanup errors shouldn't fail the build
                }
            }
        }
    }
}
