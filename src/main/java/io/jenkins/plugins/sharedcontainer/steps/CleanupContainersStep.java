package io.jenkins.plugins.sharedcontainer.steps;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.TaskListener;
import io.jenkins.plugins.sharedcontainer.service.ContainerManager;
import java.io.Serializable;
import java.util.Set;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

public class CleanupContainersStep extends Step implements Serializable {
    private static final long serialVersionUID = 1L;

    @DataBoundConstructor
    public CleanupContainersStep() {}

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new CleanupContainersStepExecution(this, context);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {
        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(TaskListener.class, Launcher.class);
        }

        @Override
        public String getFunctionName() {
            return "cleanupContainers";
        }

        @Override
        public String getDisplayName() {
            return "Cleanup All Shared Containers";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return false;
        }
    }

    static class CleanupContainersStepExecution extends SynchronousNonBlockingStepExecution<Void> {
        CleanupContainersStepExecution(CleanupContainersStep step, StepContext context) {
            super(context);
        }

        @Override
        protected Void run() throws Exception {
            TaskListener listener = getContext().get(TaskListener.class);
            Launcher launcher = getContext().get(Launcher.class);

            listener.getLogger().println("Cleaning up all shared containers...");
            ContainerManager.cleanupAll(launcher, listener);
            listener.getLogger().println("Container cleanup completed");

            return null;
        }
    }
}
