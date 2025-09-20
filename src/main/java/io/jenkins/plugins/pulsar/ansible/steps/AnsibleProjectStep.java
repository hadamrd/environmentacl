package io.jenkins.plugins.pulsar.ansible.steps;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class AnsibleProjectStep extends Step implements Serializable {
    private static final Logger LOGGER = Logger.getLogger(AnsibleProjectStep.class.getName());
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

            try {
                Object result = context.newBodyInvoker()
                        .withContext(ansibleContext)
                        .withContext(ansibleContext.getContainer())
                        .start()
                        .get(); // Pattern synchrone simple

                return null;

            } finally {
                // Cleanup direct, pas besoin de callbacks
                ansibleContext.release(step.isCleanup(), context.get(Launcher.class), listener);
            }
        }
    }
}
