package io.jenkins.plugins.sharedcontainer.steps;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.sharedcontainer.service.ContainerManager;
import java.io.Serializable;
import java.util.Set;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

// ============================================================================
// 1. SHARED CONTAINER STEP
// ============================================================================

public class SharedContainerStep extends Step implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final String CONTAINER_KEY = "SHARED_CONTAINER_INSTANCE";

    private final String image;
    private boolean keepContainer = false;

    @DataBoundConstructor
    public SharedContainerStep(String image) {
        this.image = image;
    }

    public String getImage() {
        return image;
    }

    public boolean isKeepContainer() {
        return keepContainer;
    }

    @DataBoundSetter
    public void setKeepContainer(boolean keepContainer) {
        this.keepContainer = keepContainer;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new SharedContainerStepExecution(this, context);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {
        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(Run.class, TaskListener.class, Launcher.class, FilePath.class);
        }

        @Override
        public String getFunctionName() {
            return "sharedContainer";
        }

        @Override
        public String getDisplayName() {
            return "Run in Shared Container";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }
    }

    static class SharedContainerStepExecution extends StepExecution {
        private final SharedContainerStep step;

        SharedContainerStepExecution(SharedContainerStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override
        public boolean start() throws Exception {
            StepContext context = getContext();
            TaskListener listener = context.get(TaskListener.class);
            Launcher launcher = context.get(Launcher.class);

            String nodeName = System.getProperty("user.name", "unknown");

            ContainerManager container = ContainerManager.getOrCreate(nodeName, step.getImage(), launcher, listener);

            try {
                // Store container in context for containerExec steps
                context.newBodyInvoker()
                        .withContext(container)
                        .withCallback(new ContainerCallback(container, step.isKeepContainer()))
                        .start();
                return false;
            } catch (Exception e) {
                container.release(step.isKeepContainer(), launcher, listener);
                throw e;
            }
        }

        @Override
        public void stop(Throwable cause) throws Exception {}
    }

    static class ContainerCallback extends org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback {
        private final ContainerManager container;
        private final boolean keepContainer;

        ContainerCallback(ContainerManager container, boolean keepContainer) {
            this.container = container;
            this.keepContainer = keepContainer;
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
            try {
                TaskListener listener = context.get(TaskListener.class);
                Launcher launcher = context.get(Launcher.class);
                container.release(keepContainer, launcher, listener);
            } catch (Exception e) {
                // Log but don't fail
            }
        }
    }
}
