package io.jenkins.plugins.pulsar.ssh.steps;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.pulsar.shared.LaunchHelper;
import io.jenkins.plugins.pulsar.ssh.service.SshAgent;

import java.io.Serializable;
import java.util.List;
import java.util.Set;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class SshAgentStep extends Step implements Serializable {
    private static final long serialVersionUID = 1L;

    private final List<String> credentialIds;
    private boolean cleanup = true;

    @DataBoundConstructor
    public SshAgentStep(List<String> credentialIds) {
        this.credentialIds = credentialIds;
    }

    public List<String> getCredentialIds() {
        return credentialIds;
    }

    public boolean isCleanup() {
        return cleanup;
    }

    @DataBoundSetter
    public void setCleanup(boolean cleanup) {
        this.cleanup = cleanup;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new SshAgentStepExecution(this, context);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {
        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(TaskListener.class, Launcher.class, Run.class);
        }

        @Override
        public String getFunctionName() {
            return "sshAgent";
        }

        @Override
        public String getDisplayName() {
            return "Start SSH Agent with keys";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }
    }

    public static class SshAgentStepExecution extends SynchronousNonBlockingStepExecution<Void> {
        private final SshAgentStep step;

        SshAgentStepExecution(SshAgentStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override
        protected Void run() throws Exception {
            StepContext context = getContext();
            TaskListener listener = context.get(TaskListener.class);
            Launcher launcher = context.get(Launcher.class);
            Run<?, ?> run = context.get(Run.class);

            String nodeName = LaunchHelper.getNodeName(context);
            SshAgent agent = SshAgent.getInstance(nodeName);

            // Start agent and load keys
            agent.start(launcher, listener);
            agent.loadKeys(step.getCredentialIds(), run, launcher, listener);

            // Execute nested block with agent available
            context.newBodyInvoker()
                    .withContext(agent)
                    .withCallback(new SshAgentBodyInvoker(agent, step.getCredentialIds(), step.isCleanup()))
                    .start();

            return null;
        }
    }

    private static class SshAgentBodyInvoker extends BodyExecutionCallback {
        private final SshAgent agent;
        private final List<String> credentialIds;
        private final boolean cleanup;

        SshAgentBodyInvoker(SshAgent agent, List<String> credentialIds, boolean cleanup) {
            this.agent = agent;
            this.credentialIds = credentialIds;
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
            try {
                TaskListener listener = context.get(TaskListener.class);
                Launcher launcher = context.get(Launcher.class);

                // Release keys
                agent.releaseKeys(credentialIds, listener);

                // Stop agent if cleanup requested and no more keys
                if (cleanup && !agent.hasKeys()) {
                    agent.stop(launcher, listener);
                }
            } catch (Exception e) {
                // Log but don't fail
            }
        }
    }
}
