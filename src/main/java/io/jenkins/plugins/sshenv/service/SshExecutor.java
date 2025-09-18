package io.jenkins.plugins.sshenv.service;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import hudson.model.TaskListener;
import io.jenkins.plugins.sshenv.model.SshEnvironment;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.List;
import java.util.Properties;

/**
 * Real SSH executor using JSch library. Handles SSH connections, authentication, and command
 * execution.
 */
public class SshExecutor {
    private final SshEnvironment environment;
    private final SSHUserPrivateKey credentials;
    private final transient PrintStream logger;

    public SshExecutor(SshEnvironment environment, SSHUserPrivateKey credentials, TaskListener listener) {
        this.environment = environment;
        this.credentials = credentials;
        this.logger = listener.getLogger();
    }

    /** Execute a command on a specific host via SSH using provided session */
    public int executeCommand(String host, String command, Session session) throws Exception {
        logger.println("💻 [" + host + ":" + environment.getPort() + "@" + environment.getUsername() + "]$ " + command);

        ChannelExec channel = null;
        try {
            // Execute command
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);

            // Setup streams for real-time output
            InputStream inputStream = channel.getInputStream();
            InputStream errorStream = channel.getErrStream();

            channel.connect(10000); // 10 second timeout

            // Stream output in real-time
            int exitCode = streamOutput(channel, inputStream, errorStream);
            return exitCode;
        } finally {
            // Cleanup channel only
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
                logger.println("🔌 SSH channel disconnected from " + host);
            }
        }
    }

    /** Create and configure SSH session with proper configuration handling */
    public Session createSshSession(String host) throws Exception {
        logger.println("🔗 Connecting to " + host + ":" + environment.getPort() + " as " + environment.getUsername());
        JSch jsch = new JSch();

        // Get SSH private keys
        List<String> privateKeys = credentials.getPrivateKeys();
        if (privateKeys.isEmpty()) {
            throw new IllegalArgumentException("No SSH private keys found for credential: " + credentials.getId());
        }

        // Add all available private keys to JSch
        for (int i = 0; i < privateKeys.size(); i++) {
            String privateKey = privateKeys.get(i);
            if (privateKey != null && !privateKey.trim().isEmpty()) {
                String keyName = credentials.getId() + "_key_" + i;
                jsch.addIdentity(keyName, privateKey.getBytes(), null, null);
                logger.println("🔑 Added SSH key: " + keyName);
            }
        }

        // Create session
        Session session = jsch.getSession(environment.getUsername(), host, environment.getPort());

        // Configure session
        Properties config = environment.getSshConfig().toJSchProperties();
        session.setConfig(config);

        logger.println("🔧 SSH Config: " + environment.getSshConfig().getSummary());

        // Connect session
        session.connect(30000); // 30 second timeout
        logger.println("✅ SSH connection established to " + host);

        return session;
    }

    /** Stream command output in real-time to Jenkins log */
    private int streamOutput(ChannelExec channel, InputStream inputStream, InputStream errorStream)
            throws IOException, InterruptedException {

        BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(inputStream));
        BufferedReader stderrReader = new BufferedReader(new InputStreamReader(errorStream));

        // Stream output until command completes
        while (!channel.isClosed()) {
            // Read stdout
            while (stdoutReader.ready()) {
                String line = stdoutReader.readLine();
                if (line != null) {
                    logger.println(line);
                }
            }

            // Read stderr
            while (stderrReader.ready()) {
                String line = stderrReader.readLine();
                if (line != null) {
                    logger.println("STDERR: " + line);
                }
            }

            Thread.sleep(100); // Small delay to prevent busy waiting
        }

        // Read any remaining output
        String line;
        while ((line = stdoutReader.readLine()) != null) {
            logger.println(line);
        }
        while ((line = stderrReader.readLine()) != null) {
            logger.println("STDERR: " + line);
        }

        return channel.getExitStatus();
    }
}