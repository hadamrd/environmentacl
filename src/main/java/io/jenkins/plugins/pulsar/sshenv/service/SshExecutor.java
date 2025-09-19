package io.jenkins.plugins.pulsar.sshenv.service;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import hudson.model.TaskListener;
import io.jenkins.plugins.pulsar.sshenv.model.SshEnvironment;
import java.io.PrintStream;
import java.util.List;

/** Jenkins-specific SSH executor that manages connection lifecycle and command execution */
public class SshExecutor implements AutoCloseable {
    private final String host;
    private final SshEnvironment environment;
    private final SSHUserPrivateKey credentials;
    private final PrintStream logger;

    // L'executor maintient la connexion
    private SshConnection connection;

    public SshExecutor(String host, SshEnvironment environment, SSHUserPrivateKey credentials, TaskListener listener) {
        this.host = host;
        this.environment = environment;
        this.credentials = credentials;
        this.logger = listener.getLogger();
    }

    /** Execute a command, ensuring connection is available */
    public int runCmd(String command) throws Exception {
        ensureConnected();

        logger.println("💻 [" + host + ":" + environment.getPort() + "@" + environment.getUsername() + "]$ " + command);

        try (SshCommandResult result = connection.executeCommand(command, 10000)) {
            return result.streamOutput(
                    line -> logger.println(line), // stdout
                    line -> logger.println("STDERR: " + line) // stderr
                    );
        }
    }

    /** Test connectivity by executing a simple command */
    public boolean testConnection() {
        try {
            int exitCode = runCmd("echo 'SSH connection test successful'");
            return exitCode == 0;
        } catch (Exception e) {
            logger.println("❌ SSH connection test failed: " + e.getMessage());
            return false;
        }
    }

    /** Ensure we have a valid connection, create/recreate if necessary */
    private void ensureConnected() throws Exception {
        if (connection == null || !connection.isConnected()) {
            if (connection != null) {
                logger.println("⚠️ SSH connection lost to " + host + ", reconnecting...");
                connection.close();
            } else {
                logger.println("🔗 Creating SSH connection to " + host);
            }

            connection = createConnection();
        }
    }

    /** Create a new SSH connection */
    private SshConnection createConnection() throws Exception {
        logger.println("🔗 Connecting to " + host + ":" + environment.getPort() + " as " + environment.getUsername());

        SshConnection newConnection = new SshConnection(host, environment.getPort(), environment.getUsername());

        // Add SSH keys
        List<String> privateKeys = credentials.getPrivateKeys();
        if (privateKeys.isEmpty()) {
            throw new IllegalArgumentException("No SSH private keys found for credential: " + credentials.getId());
        }

        for (int i = 0; i < privateKeys.size(); i++) {
            String privateKey = privateKeys.get(i);
            if (privateKey != null && !privateKey.trim().isEmpty()) {
                String keyName = credentials.getId() + "_key_" + i;
                newConnection.addIdentity(keyName, privateKey.getBytes());
                logger.println("🔑 Added SSH key: " + keyName);
            }
        }

        // Connect
        newConnection.connect(environment.getSshConfig().toJSchProperties(), 30000);
        logger.println("✅ SSH connection established to " + host);

        return newConnection;
    }

    @Override
    public void close() {
        if (connection != null) {
            connection.close();
            logger.println("🔌 SSH connection closed to " + host);
            connection = null;
        }
    }
}
