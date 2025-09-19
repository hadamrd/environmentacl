package io.jenkins.plugins.pulsar.sharedcontainer.steps;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.TaskListener;
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

public class ContainerExecStep extends Step implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String script;
    private String user;

    @DataBoundConstructor
    public ContainerExecStep(String script) {
        this.script = script;
    }

    public String getScript() {
        return script;
    }

    public String getUser() {
        return user;
    }

    @DataBoundSetter
    public void setUser(String user) {
        this.user = user;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new ContainerExecStepExecution(this, context);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {
        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(TaskListener.class, Launcher.class, ContainerManager.class);
        }

        @Override
        public String getFunctionName() {
            return "containerExec";
        }

        @Override
        public String getDisplayName() {
            return "Execute Command in Shared Container";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return false;
        }
    }

    public static class ContainerExecStepExecution extends SynchronousNonBlockingStepExecution<Integer> {
        private final ContainerExecStep step;

        ContainerExecStepExecution(ContainerExecStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override
        protected Integer run() throws Exception {
            TaskListener listener = getContext().get(TaskListener.class);
            Launcher launcher = getContext().get(Launcher.class);

            // Get container directly from context - no wrapper needed!
            ContainerManager container = getContext().get(ContainerManager.class);
            if (container == null) {
                throw new IllegalStateException("containerExec must be used inside a sharedContainer block");
            }

            return container.execute(step.getScript(), step.getUser(), launcher, listener);
        }
    }
}
