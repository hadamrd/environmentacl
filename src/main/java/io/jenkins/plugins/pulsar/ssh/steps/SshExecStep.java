package io.jenkins.plugins.pulsar.ssh.steps;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.TaskListener;
import java.io.Serializable;
import java.util.Set;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

public class SshExecStep extends Step implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String command;

    @DataBoundConstructor
    public SshExecStep(String command) {
        this.command = command;
    }

    public String getCommand() {
        return command;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new SshExecStepExecution(this, context);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(TaskListener.class, Launcher.class, SshContext.class);
        }

        @Override
        public String getFunctionName() {
            return "sshExec";
        }

        @Override
        public String getDisplayName() {
            return "Execute SSH command";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return false;
        }
    }

    public static class SshExecStepExecution extends SynchronousNonBlockingStepExecution<Integer> {
        private final SshExecStep step;

        SshExecStepExecution(SshExecStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override
        protected Integer run() throws Exception {
            SshContext sshContext = getContext().get(SshContext.class);

            if (sshContext == null) {
                throw new Exception("sshExec can only be used inside an sshHost block");
            }

            // Execute the command via SSH
            int exitCode = sshContext.getExecutor().runCmd(step.getCommand());

            return exitCode;
        }
    }
}
