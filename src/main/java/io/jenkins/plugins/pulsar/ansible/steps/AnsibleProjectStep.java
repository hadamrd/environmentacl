package io.jenkins.plugins.pulsar.ansible.steps;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class AnsibleProjectStep extends Step implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String projectId;
    private final Map<String, String> version; // {ref: "main", type: "branch"}

    private List<String> containerOptions;
    private boolean cleanup = true;
    private int timeoutHours = 8;

    @DataBoundConstructor
    public AnsibleProjectStep(String projectId, Map<String, String> version) {
        this.projectId = projectId;
        this.version = version;
    }

    public String getProjectId() {
        return projectId;
    }

    public Map<String, String> getVersion() {
        return version;
    }

    public List<String> getContainerOptions() {
        return containerOptions;
    }

    public boolean isCleanup() {
        return cleanup;
    }

    public int getTimeoutHours() {
        return timeoutHours;
    }

    @DataBoundSetter
    public void setContainerOptions(List<String> containerOptions) {
        this.containerOptions = containerOptions;
    }

    @DataBoundSetter
    public void setCleanup(boolean cleanup) {
        this.cleanup = cleanup;
    }

    @DataBoundSetter
    public void setTimeoutHours(int timeoutHours) {
        this.timeoutHours = Math.max(1, Math.min(24, timeoutHours));
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new AnsibleProjectStepExecution(this, context);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {
        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(TaskListener.class, Launcher.class, Run.class);
        }

        @Override
        public String getFunctionName() {
            return "ansibleProject";
        }

        @Override
        public String getDisplayName() {
            return "Execute within Ansible project environment";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }
    }

    public static class AnsibleProjectStepExecution extends SynchronousNonBlockingStepExecution<Void> {
        private final AnsibleProjectStep step;

        AnsibleProjectStepExecution(AnsibleProjectStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override
        protected Void run() throws Exception {
            StepContext context = getContext();
            TaskListener listener = context.get(TaskListener.class);

            // Create Ansible context - this handles everything (container, SSH, vaults, project setup)
            AnsibleContext ansibleContext = AnsibleContext.getOrCreate(
                    step.getProjectId(), step.getVersion(), context, step.getContainerOptions());

            listener.getLogger().println("=== Ansible Project: " + step.getProjectId() + " ===");
            listener.getLogger().println("Version: " + step.getVersion());
            listener.getLogger().println("Project Root: " + ansibleContext.getProjectRoot());

            // Execute nested block with AnsibleContext available
            // ContainerManager is available through ansibleContext.getContainer() if needed
            context.newBodyInvoker()
                    .withContext(ansibleContext)
                    .withContext(ansibleContext.getContainer()) // Make container available for containerExec steps
                    .withCallback(new AnsibleBodyInvoker(ansibleContext, step.isCleanup()))
                    .start();

            return null;
        }
    }

    private static class AnsibleBodyInvoker extends BodyExecutionCallback {
        private final AnsibleContext ansibleContext;
        private final boolean cleanup;

        AnsibleBodyInvoker(AnsibleContext ansibleContext, boolean cleanup) {
            this.ansibleContext = ansibleContext;
            this.cleanup = cleanup;
        }

        @Override
        public void onSuccess(StepContext context, Object result) {
            cleanup(context);
            context.onSuccess(result);
        }

        @Override
        public void onFailure(StepContext context, Throwable t) {
            cleanup(context);
            context.onFailure(t);
        }

        private void cleanup(StepContext context) {
            TaskListener listener = null;
            Launcher launcher = null;

            try {
                listener = context.get(TaskListener.class);
                launcher = context.get(Launcher.class);

                // Use AnsibleContext.release() which handles container + SSH + vault cleanup
                ansibleContext.release(cleanup, launcher, listener);
            } catch (Exception e) {
                // Log but don't fail
                if (listener != null) {
                    listener.getLogger().println("Warning: Failed to cleanup Ansible context: " + e.getMessage());
                }
            }
        }
    }
}
