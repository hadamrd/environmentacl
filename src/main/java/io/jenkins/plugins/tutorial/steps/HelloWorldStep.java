package io.jenkins.plugins.tutorial.steps;

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
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Main step class that defines a custom Jenkins pipeline step.
 * This class holds the step's configuration and creates the execution.
 */
public class HelloWorldStep extends Step implements Serializable {
    private static final long serialVersionUID = 1L;

    // Required parameter - will be passed when the step is called
    private final String message;
    
    // Optional parameter with default value - can be set via setter
    private boolean uppercase = false;

    /**
     * Constructor for required parameters.
     * @DataBoundConstructor tells Jenkins this constructor should be used
     * when creating the step from pipeline script parameters.
     */
    @DataBoundConstructor
    public HelloWorldStep(String message) {
        this.message = message;
    }

    // Standard getter for required parameter
    public String getMessage() {
        return message;
    }

    // Standard getter for optional parameter
    public boolean isUppercase() {
        return uppercase;
    }

    /**
     * Setter for optional parameters.
     * @DataBoundSetter tells Jenkins this can be set as a named parameter
     * in the pipeline script (e.g., helloWorld(message: "test", uppercase: true))
     */
    @DataBoundSetter
    public void setUppercase(boolean uppercase) {
        this.uppercase = uppercase;
    }

    /**
     * Creates and returns the step execution.
     * This is called by Jenkins when the step runs in a pipeline.
     */
    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new HelloWorldStepExecution(this, context);
    }

    /**
     * Descriptor class that provides metadata about this step to Jenkins.
     * @Extension tells Jenkins to register this as a step descriptor.
     */
    @Extension
    public static class DescriptorImpl extends StepDescriptor {
        
        /**
         * Declares what Jenkins context objects this step needs to function.
         * TaskListener: for logging output
         * Launcher: for executing commands
         */
        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(TaskListener.class, Launcher.class);
        }

        /**
         * The function name used in pipeline scripts.
         * Users will call: helloWorld("message")
         */
        @Override
        public String getFunctionName() {
            return "helloWorld";
        }

        /**
         * Human-readable name shown in Jenkins UI
         */
        @Override
        public String getDisplayName() {
            return "Say Hello World";
        }

        /**
         * Whether this step accepts a block of nested steps.
         * False = simple step like: helloWorld("message")
         * True = block step like: helloWorld("message") { nested steps }
         */
        @Override
        public boolean takesImplicitBlockArgument() {
            return false;
        }
    }

    /**
     * The actual execution logic for the step.
     * Extends SynchronousNonBlockingStepExecution because our step:
     * - Runs synchronously (doesn't need to wait for external events)
     * - Is non-blocking (doesn't tie up the Jenkins executor thread)
     * - Returns an Integer (the exit code from the command)
     */
    public static class HelloWorldStepExecution extends SynchronousNonBlockingStepExecution<Integer> {
        private final HelloWorldStep step;

        HelloWorldStepExecution(HelloWorldStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        /**
         * The main execution method - contains the actual step logic.
         * This runs when the step executes in a pipeline.
         */
        @Override
        protected Integer run() throws Exception {
            // Get Jenkins context objects we declared in getRequiredContext()
            TaskListener listener = getContext().get(TaskListener.class);
            Launcher launcher = getContext().get(Launcher.class);

            // Process the message based on step configuration
            String message = step.getMessage();
            if (step.isUppercase()) {
                message = message.toUpperCase();
            }

            // Use Jenkins launcher to execute a command safely
            // This handles platform differences and logging automatically
            return launcher.launch()
                    .cmds("echo", "Hello World: " + message)  // Command and arguments
                    .stdout(listener.getLogger())             // Where to send output
                    .stderr(listener.getLogger())             // Where to send errors
                    .start()                                  // Start the process
                    .join();                                  // Wait for completion and return exit code
        }
    }
}