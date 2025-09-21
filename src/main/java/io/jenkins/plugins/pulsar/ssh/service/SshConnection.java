package io.jenkins.plugins.pulsar.ssh.service;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import java.io.InputStream;
import java.util.Properties;

/** Low-level wrapper around JSch library for SSH operations */
public class SshConnection implements AutoCloseable {
    private final JSch jsch;
    private Session session;
    private final String host;
    private final int port;
    private final String username;

    public SshConnection(String host, int port, String username) {
        this.jsch = new JSch();
        this.host = host;
        this.port = port;
        this.username = username;
    }

    public void addIdentity(String name, byte[] privateKey) throws Exception {
        jsch.addIdentity(name, privateKey, null, null);
    }

    public void connect(Properties config, int timeoutMs) throws Exception {
        session = jsch.getSession(username, host, port);
        session.setConfig(config);
        session.connect(timeoutMs);
    }

    public boolean isConnected() {
        return session != null && session.isConnected();
    }

    public SshCommandResult executeCommand(String command, int timeoutMs) throws Exception {
        ChannelExec channel = null;
        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);

            InputStream stdout = channel.getInputStream();
            InputStream stderr = channel.getErrStream();

            channel.connect(timeoutMs);

            return new SshCommandResult(channel, stdout, stderr);
        } finally {
            if (channel != null && channel.isConnected()) {
                // Le channel sera fermé par SshCommandResult
            }
        }
    }

    @Override
    public void close() {
        if (session != null && session.isConnected()) {
            session.disconnect();
            session = null;
        }
    }
}
