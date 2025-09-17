package io.jenkins.plugins.sharedcontainer.service;

import hudson.Launcher;
import hudson.model.TaskListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class LaunchHelper {

    /** Execute a Docker command quietly and capture output to a stream */
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

    /** Execute a Docker command quietly and discard output */
    public static int executeQuietly(Launcher launcher, List<String> command, int timeoutSeconds, TaskListener listener)
            throws IOException, InterruptedException {

        return executeQuietly(launcher, command, null, timeoutSeconds, listener);
    }

    /** Execute a Docker command and show output to user */
    public static int executeVisible(Launcher launcher, List<String> command, TaskListener listener)
            throws IOException, InterruptedException {

        return launcher.launch()
                .cmds(command)
                .stdout(listener.getLogger())
                .stderr(listener.getLogger())
                .quiet(true) // Still don't show the command itself
                .start()
                .join();
    }

    /** Execute a Docker command and capture output as string */
    public static String executeAndCapture(
            Launcher launcher, List<String> command, int timeoutSeconds, TaskListener listener)
            throws IOException, InterruptedException {

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int exitCode = executeQuietly(launcher, command, output, timeoutSeconds, listener);

        if (exitCode == 0) {
            return output.toString("UTF-8").trim();
        }
        return null;
    }
}
