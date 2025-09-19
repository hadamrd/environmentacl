package io.jenkins.plugins.pulsar.sshenv.service;

import com.jcraft.jsch.ChannelExec;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.function.Consumer;

/** Encapsulates the result of an SSH command execution */
public class SshCommandResult implements AutoCloseable {
    private final ChannelExec channel;
    private final InputStream stdout;
    private final InputStream stderr;

    public SshCommandResult(ChannelExec channel, InputStream stdout, InputStream stderr) {
        this.channel = channel;
        this.stdout = stdout;
        this.stderr = stderr;
    }

    public int streamOutput(Consumer<String> stdoutConsumer, Consumer<String> stderrConsumer) throws Exception {

        BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(stdout));
        BufferedReader stderrReader = new BufferedReader(new InputStreamReader(stderr));

        // Stream output until command completes
        while (!channel.isClosed()) {
            // Read stdout
            while (stdoutReader.ready()) {
                String line = stdoutReader.readLine();
                if (line != null) {
                    stdoutConsumer.accept(line);
                }
            }

            // Read stderr
            while (stderrReader.ready()) {
                String line = stderrReader.readLine();
                if (line != null) {
                    stderrConsumer.accept(line);
                }
            }

            Thread.sleep(100);
        }

        // Read remaining output
        String line;
        while ((line = stdoutReader.readLine()) != null) {
            stdoutConsumer.accept(line);
        }
        while ((line = stderrReader.readLine()) != null) {
            stderrConsumer.accept(line);
        }

        return channel.getExitStatus();
    }

    @Override
    public void close() {
        if (channel != null && channel.isConnected()) {
            channel.disconnect();
        }
    }
}
