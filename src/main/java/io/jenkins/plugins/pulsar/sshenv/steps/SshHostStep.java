package io.jenkins.plugins.pulsar.sshenv.steps;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.pulsar.sshenv.config.SshEnvironmentsGlobalConfiguration;
import io.jenkins.plugins.pulsar.sshenv.model.SshEnvironment;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.Set;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

public class SshHostStep extends Step implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String host;

    @DataBoundConstructor
    public SshHostStep(String host) {
        this.host = host;
    }

    public String getHost() {
        return host;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new SshHostStepExecution(this, context);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(TaskListener.class, Launcher.class, Run.class);
        }

        @Override
        public String getFunctionName() {
            return "sshHost";
        }

        @Override
        public String getDisplayName() {
            return "Execute commands on SSH host";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }
    }

    public static class SshHostStepExecution extends SynchronousNonBlockingStepExecution<Void> {
        private final SshHostStep step;

        SshHostStepExecution(SshHostStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override
        protected Void run() throws Exception {
            StepContext context = getContext();
            TaskListener listener = context.get(TaskListener.class);
            PrintStream logger = listener.getLogger();

            // Find environment for this host
            SshEnvironmentsGlobalConfiguration config = SshEnvironmentsGlobalConfiguration.get();
            SshEnvironment environment = config.getEnvironmentByHost(step.getHost());

            if (environment == null) {
                throw new Exception("Host '" + step.getHost() + "' not found in any SSH environment.");
            }

            // Get SSH credentials
            Run<?, ?> run = context.get(Run.class);
            SSHUserPrivateKey credentials = CredentialsProvider.findCredentialById(
                    environment.getSshCredentialId(), SSHUserPrivateKey.class, run);

            if (credentials == null) {
                throw new Exception("SSH credentials '" + environment.getSshCredentialId() + "' not found.");
            }

            logger.println("=== SSH Host: " + step.getHost() + " ===");
            logger.println("Environment: " + environment.getName());
            logger.println("User: " + environment.getUsername() + "@" + step.getHost() + ":" + environment.getPort());

            // Create SSH context
            SshContext sshContext = new SshContext(step.getHost(), environment, credentials, listener);

            // Test connection
            if (!sshContext.getExecutor().testConnection()) {
                throw new SshPluginException("Failed to establish SSH connection to " + step.getHost());
            }

            // Execute the nested block with SSH context
            context.newBodyInvoker()
                    .withContext(sshContext) // make ssh session and utilities available to steps inside
                    .withCallback(new SshBodyInvoker(sshContext, listener))
                    .start();

            return null;
        }
    }

    private static class SshBodyInvoker extends BodyExecutionCallback {
        private final SshContext sshContext;
        private final transient TaskListener listener;

        SshBodyInvoker(SshContext sshContext, TaskListener listener) {
            this.sshContext = sshContext;
            this.listener = listener;
        }

        @Override
        public void onSuccess(StepContext context, Object result) {
            cleanup();
            context.onSuccess(result);
        }

        @Override
        public void onFailure(StepContext context, Throwable t) {
            cleanup();
            context.onFailure(t);
        }

        private void cleanup() {
            if (sshContext != null) {
                try {
                    sshContext.cleanup();
                } catch (Exception e) {
                    listener.getLogger().println("⚠️ Failed to close SSH session: " + e.getMessage());
                }
            }
        }
    }
}
