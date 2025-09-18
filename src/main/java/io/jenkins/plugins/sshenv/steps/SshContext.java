package io.jenkins.plugins.sshenv.steps;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.jcraft.jsch.Session;
import hudson.model.TaskListener;
import io.jenkins.plugins.sshenv.model.SshEnvironment;
import io.jenkins.plugins.sshenv.service.SshExecutor;
import java.io.Serializable;

public class SshContext implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String host;
    private final SshEnvironment environment;
    private final SSHUserPrivateKey credentials;
    private transient final SshExecutor executor;
    private transient Session session;
    private transient final TaskListener listener;

    public SshContext(String host, SshEnvironment environment, SSHUserPrivateKey credentials, TaskListener listener) {
        this.host = host;
        this.environment = environment;
        this.credentials = credentials;
        this.listener = listener;
        this.executor = new SshExecutor(environment, credentials, listener);
        // Initialize session
        try {
            this.session = executor.createSshSession(host);
        } catch (Exception e) {
            listener.getLogger().println("❌ Failed to create SSH session for " + host + ": " + e.getMessage());
            throw new SshPluginException("Failed to initialize SSH session", e);
        }
    }

    public String getHost() {
        return host;
    }

    public SshEnvironment getEnvironment() {
        return environment;
    }

    public SSHUserPrivateKey getCredentials() {
        return credentials;
    }

    public SshExecutor getExecutor() {
        return executor;
    }

    public Session getSession() {
        return session;
    }

    /** Execute a command using the existing session */
    public int executeCommand(String command) throws Exception {
        if (session == null || !session.isConnected()) {
            listener.getLogger().println("⚠️ SSH session is not connected for " + host + ", attempting to reconnect...");
            session = executor.createSshSession(host);
        }
        return executor.executeCommand(host, command, session);
    }

    
    /** Test SSH connectivity to a host */
    public boolean testConnection(String host) {
        try {
            int exitCode = executeCommand("echo 'SSH connection test successful'");
            return exitCode == 0;
        } catch (Exception e) {
            listener.getLogger().println("❌ SSH connection test failed: " + e.getMessage());
            return false;
        }
    }

    public void cleanup() {
        if (session != null && session.isConnected()) {
            session.disconnect();
            listener.getLogger().println("🔌 SSH session disconnected from " + host);
            session = null;
        }
    }
}