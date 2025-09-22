package io.jenkins.plugins.pulsar.shared;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.TaskListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jenkinsci.plugins.workflow.steps.StepContext;

public class LaunchHelper {

    public static String getNodeName(StepContext stepContext) {
        try {
            EnvVars envVars = stepContext.get(EnvVars.class);
            if (envVars != null) {
                String nodeName = envVars.get("NODE_NAME");
                // NODE_NAME is empty string for built-in node, so we use "master"
                return (nodeName != null && !nodeName.trim().isEmpty()) ? nodeName : "master";
            }
        } catch (Exception e) {
            // Ignore
        }
        return "master";
    }

    /** Execute with PrintStream output */
    public static int executeQuietly(
            Launcher launcher, List<String> command, PrintStream output, int timeoutSeconds, TaskListener listener)
            throws IOException, InterruptedException {

        return launcher.launch()
                .cmds(command)
                .stdout(output)
                .stderr(output)
                .quiet(true)
                .start()
                .joinWithTimeout(timeoutSeconds, TimeUnit.SECONDS, listener);
    }

    /** Execute with ByteArrayOutputStream output */
    public static int executeQuietly(
            Launcher launcher,
            List<String> command,
            ByteArrayOutputStream output,
            int timeoutSeconds,
            TaskListener listener)
            throws IOException, InterruptedException {

        OutputStream stdout = output != null ? output : new ByteArrayOutputStream();
        OutputStream stderr = output != null ? output : new ByteArrayOutputStream();

        return launcher.launch()
                .cmds(command)
                .stdout(stdout)
                .stderr(stderr)
                .quiet(true)
                .start()
                .joinWithTimeout(timeoutSeconds, TimeUnit.SECONDS, listener);
    }

    /** Execute quietly and discard all output - NO AMBIGUITY */
    public static int executeQuietlyDiscardOutput(
            Launcher launcher, List<String> command, int timeoutSeconds, TaskListener listener)
            throws IOException, InterruptedException {

        // Create throwaway streams to discard output
        ByteArrayOutputStream devNull = new ByteArrayOutputStream();

        return launcher.launch()
                .cmds(command)
                .stdout(devNull)
                .stderr(devNull)
                .quiet(true)
                .start()
                .joinWithTimeout(timeoutSeconds, TimeUnit.SECONDS, listener);
    }

    /** Execute and show output to user */
    public static int executeVisible(Launcher launcher, List<String> command, TaskListener listener)
            throws IOException, InterruptedException {

        return launcher.launch()
                .cmds(command)
                .stdout(listener.getLogger())
                .stderr(listener.getLogger())
                .quiet(true)
                .start()
                .join();
    }

    /** Execute and capture output as string */
    public static String executeAndCapture(
            Launcher launcher, List<String> command, int timeoutSeconds, TaskListener listener)
            throws IOException, InterruptedException {

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        executeQuietly(launcher, command, output, timeoutSeconds, listener);

        String result = output.toString("UTF-8").trim();

        // Return output even if command failed (useful for error diagnosis)
        return result.isEmpty() ? null : result;
    }
}
