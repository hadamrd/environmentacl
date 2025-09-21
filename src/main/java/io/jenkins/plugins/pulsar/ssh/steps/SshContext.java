package io.jenkins.plugins.pulsar.ssh.steps;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import hudson.model.TaskListener;
import io.jenkins.plugins.pulsar.ssh.model.SshEnvironment;
import io.jenkins.plugins.pulsar.ssh.service.SshExecutor;

import java.io.Serializable;

public class SshContext implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String host;
    private final SshEnvironment environment;
    private final SSHUserPrivateKey credentials;
    private final transient SshExecutor executor;

    public SshContext(String host, SshEnvironment environment, SSHUserPrivateKey credentials, TaskListener listener) {
        this.host = host;
        this.environment = environment;
        this.credentials = credentials;
        this.executor = new SshExecutor(host, environment, credentials, listener);
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

    public void cleanup() {
        if (executor != null) {
            executor.close();
        }
    }
}
