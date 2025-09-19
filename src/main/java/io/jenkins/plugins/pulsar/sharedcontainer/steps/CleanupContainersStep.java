package io.jenkins.plugins.pulsar.sharedcontainer.steps;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.TaskListener;
import io.jenkins.plugins.pulsar.sharedcontainer.service.ContainerCleaner;
import io.jenkins.plugins.pulsar.sharedcontainer.service.ContainerManager;
import java.io.Serializable;
import java.util.Set;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class CleanupContainersStep extends Step implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean force = false;

    @DataBoundConstructor
    public CleanupContainersStep() {}

    public boolean isForce() {
        return force;
    }

    @DataBoundSetter
    public void setForce(boolean force) {
        this.force = force;
    }

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
            return "Cleanup Shared Containers";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return false;
        }
    }

    static class CleanupContainersStepExecution extends SynchronousNonBlockingStepExecution<Void> {
        private final CleanupContainersStep step;

        CleanupContainersStepExecution(CleanupContainersStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override
        protected Void run() throws Exception {
            TaskListener listener = getContext().get(TaskListener.class);
            Launcher launcher = getContext().get(Launcher.class);

            if (step.isForce()) {
                // Force cleanup: find and destroy ALL managed containers
                listener.getLogger().println("Force cleanup: removing ALL managed containers...");
                ContainerCleaner.cleanupAllManagedContainers(launcher, listener);
            } else {
                // Normal cleanup: only active containers known to this session
                listener.getLogger().println("Cleaning up active shared containers...");
                ContainerManager.cleanupAll(launcher, listener);
            }

            return null;
        }
    }
}
