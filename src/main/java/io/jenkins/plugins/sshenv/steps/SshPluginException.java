package io.jenkins.plugins.sshenv.steps;

public class SshPluginException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SshPluginException(String message) {
        super(message);
    }

    public SshPluginException(String message, Throwable cause) {
        super(message, cause);
    }
}