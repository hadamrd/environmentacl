package io.jenkins.plugins.sharedcontainer.steps;

import hudson.Launcher;
import hudson.model.TaskListener;
import io.jenkins.plugins.sharedcontainer.service.ContainerManager;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;

public class ContainerExecStepExecution extends SynchronousNonBlockingStepExecution<Integer> {
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
