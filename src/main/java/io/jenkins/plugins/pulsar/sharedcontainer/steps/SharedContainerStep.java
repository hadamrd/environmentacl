package io.jenkins.plugins.pulsar.sharedcontainer.steps;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.pulsar.shared.LaunchHelper;
import io.jenkins.plugins.pulsar.sharedcontainer.service.ContainerManager;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class SharedContainerStep extends Step implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String image;
    private boolean cleanup = true;
    private String options = "";
    private int timeoutHours = 8;

    @DataBoundConstructor
    public SharedContainerStep(String image) {
        this.image = image;
    }

    public String getImage() {
        return image;
    }

    public boolean getCleanup() {
        return cleanup;
    }

    public String getOptions() {
        return options;
    }

    public int getTimeoutHours() {
        return timeoutHours;
    }

    @DataBoundSetter
    public void setCleanup(boolean keepContainer) {
        this.cleanup = keepContainer;
    }

    @DataBoundSetter
    public void setOptions(String options) {
        this.options = options != null ? options : "";
    }

    @DataBoundSetter
    public void setTimeoutHours(int timeoutHours) {
        // Clamp between 1 and 24 hours for safety
        this.timeoutHours = Math.max(1, Math.min(24, timeoutHours));
    }

    /** Build Docker run command with user options */
    public List<String> buildDockerRunArgs() {
        List<String> args = new ArrayList<>();

        // Basic docker run command
        args.addAll(Arrays.asList("docker", "run", "-d"));

        // Add user options if provided, otherwise default volume
        if (options != null && !options.trim().isEmpty()) {
            // Split the options string and add to command
            // Simple split on spaces (could be enhanced for quoted args if needed)
            String[] optionParts = options.trim().split("\\s+");
            args.addAll(Arrays.asList(optionParts));
        }

        return args;
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
            FilePath workspace = context.get(FilePath.class);

            // Get the actual Jenkins node name properly
            String nodeName = LaunchHelper.getNodeName(context);

            listener.getLogger().println("Node name detected: " + nodeName);

            // Updated call - now passes the step for options support
            ContainerManager container =
                    ContainerManager.getOrCreate(nodeName, step.getImage(), step, launcher, listener);

            try {
                // Store container in context for containerExec steps
                context.newBodyInvoker()
                        .withContext(container)
                        .withCallback(new ContainerCallback(container, step.getCleanup()))
                        .start();
                return false;
            } catch (Exception e) {
                container.release(step.getCleanup(), launcher, listener);
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

            } catch (Exception e) {
                // Log but don't fail
            }
        }
    }
}
