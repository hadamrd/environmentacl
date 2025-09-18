package io.jenkins.plugins.tutorial.steps;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.TaskListener;
import io.jenkins.plugins.tutorial.config.HelloWorldGlobalConfiguration;
import java.io.Serializable;
import java.util.Set;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Simple HelloWorld step that demonstrates accessing JCasC global configuration. This step gets a
 * random greeting from the global configuration.
 */
public class SimpleHelloWorldStep extends Step implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Constructor - no parameters needed for this simple example. */
    @DataBoundConstructor
    public SimpleHelloWorldStep() {
        // No parameters needed
    }

    /** Creates and returns the step execution. */
    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new SimpleHelloWorldStepExecution(this, context);
    }

    /** Descriptor that tells Jenkins about this step. */
    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(TaskListener.class, Launcher.class);
        }

        @Override
        public String getFunctionName() {
            return "randomHello"; // Pipeline users call: randomHello()
        }

        @Override
        public String getDisplayName() {
            return "Random Hello Message";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return false;
        }
    }

    /** Simple execution that just gets a random greeting from JCasC config. */
    public static class SimpleHelloWorldStepExecution extends SynchronousNonBlockingStepExecution<Integer> {
        private final SimpleHelloWorldStep step;

        SimpleHelloWorldStepExecution(SimpleHelloWorldStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        /** Main execution - shows the simplest way to access JCasC configuration. */
        @Override
        protected Integer run() throws Exception {
            TaskListener listener = getContext().get(TaskListener.class);
            Launcher launcher = getContext().get(Launcher.class);

            // ACCESS JCASC CONFIG - This is all you need!
            HelloWorldGlobalConfiguration config = HelloWorldGlobalConfiguration.get();

            // Get a random greeting from the configured list
            String randomMessage = config.getRandomGreeting();

            listener.getLogger().println("Using random greeting from JCasC config: " + randomMessage);

            // Echo the random message
            return launcher.launch()
                    .cmds("echo", randomMessage)
                    .stdout(listener.getLogger())
                    .stderr(listener.getLogger())
                    .start()
                    .join();
        }
    }
}
