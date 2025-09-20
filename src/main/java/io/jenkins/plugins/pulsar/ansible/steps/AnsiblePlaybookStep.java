package io.jenkins.plugins.pulsar.ansible.steps;

import hudson.AbortException;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.TaskListener;
import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class AnsiblePlaybookStep extends Step implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String playbook;
    private final String envName;

    private String user = "root";
    private Map<String, Object> extraVars;
    private String options;

    @DataBoundConstructor
    public AnsiblePlaybookStep(String playbook, String envName) {
        this.playbook = playbook;
        this.envName = envName;
    }

    public String getPlaybook() {
        return playbook;
    }

    public String getEnvName() {
        return envName;
    }

    public String getUser() {
        return user;
    }

    @DataBoundSetter
    public void setUser(String user) {
        this.user = user;
    }

    public Map<String, Object> getExtraVars() {
        return extraVars;
    }

    @DataBoundSetter
    public void setExtraVars(Map<String, Object> extraVars) {
        this.extraVars = extraVars;
    }

    public String getOptions() {
        return options;
    }

    @DataBoundSetter
    public void setOptions(String options) {
        this.options = options;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new AnsiblePlaybookStepExecution(this, context);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {
        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(TaskListener.class, Launcher.class, AnsibleContext.class);
        }

        @Override
        public String getFunctionName() {
            return "ansiblePlaybook";
        }

        @Override
        public String getDisplayName() {
            return "Run Ansible Playbook";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return false;
        }
    }

    public static class AnsiblePlaybookStepExecution extends SynchronousNonBlockingStepExecution<Integer> {
        private final AnsiblePlaybookStep step;

        AnsiblePlaybookStepExecution(AnsiblePlaybookStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override
        protected Integer run() throws Exception {
            TaskListener listener = getContext().get(TaskListener.class);
            Launcher launcher = getContext().get(Launcher.class);
            AnsibleContext ansibleContext = getContext().get(AnsibleContext.class);

            if (ansibleContext == null) {
                listener.error("ansiblePlaybook must be used inside an ansibleProject block");
                return -1; // Return error code instead of throwing
            }

            listener.getLogger().println("=== Running Ansible Playbook ===");
            listener.getLogger().println("Playbook: " + step.playbook);
            listener.getLogger().println("Environment: " + step.envName);
            listener.getLogger().println("User: " + step.user);

            try {
                int exitCode = ansibleContext.runPlaybook(
                        step.playbook, step.envName, step.extraVars, 
                        step.options, step.user, launcher, listener);

                listener.getLogger().println("Run playbook finished with code: " + exitCode);

                if (exitCode != 0) {
                    listener.error("Ansible playbook failed with exit code: " + exitCode);
                    // Don't throw - just return the exit code
                }

                return exitCode; // Always return the code, let parent handle it

            } catch (Exception e) {
                // Log error but don't throw
                listener.error("Ansible playbook execution failed: " + e.getMessage());
                e.printStackTrace(listener.getLogger());
                return -1; // Return error code
            }
        }
    }
}
