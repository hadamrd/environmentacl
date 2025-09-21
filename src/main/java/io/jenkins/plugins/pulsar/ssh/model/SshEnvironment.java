package io.jenkins.plugins.pulsar.ssh.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Model class representing an SSH environment configuration. Contains a group of VMs with their
 * hostnames and SSH credentials.
 */
public class SshEnvironment implements Serializable {
    private static final long serialVersionUID = 1L;

    // Required fields
    private final String name;
    private final List<String> hosts;

    // Optional fields
    private String sshCredentialId;
    private String username = "root";
    private int port = 22;

    // Advanced SSH configuration (optional)
    private SshConfig sshConfig;

    /**
     * Constructor for required fields.
     *
     * @param name Environment name (e.g., "dev", "staging", "prod")
     * @param hosts List of hostnames or IP addresses
     */
    @DataBoundConstructor
    public SshEnvironment(String name, List<String> hosts) {
        this.name = name;
        this.hosts = hosts != null ? new ArrayList<>(hosts) : new ArrayList<>();
        // Initialize with default SSH configuration
        this.sshConfig = new SshConfig();
    }

    // Getters for all fields
    public String getName() {
        return name;
    }

    public List<String> getHosts() {
        return hosts != null ? hosts : new ArrayList<>();
    }

    public String getSshCredentialId() {
        return sshCredentialId;
    }

    public String getUsername() {
        return username;
    }

    public int getPort() {
        return port;
    }

    public SshConfig getSshConfig() {
        return sshConfig != null ? sshConfig : new SshConfig();
    }

    // Setters for optional fields
    @DataBoundSetter
    public void setSshCredentialId(String sshCredentialId) {
        this.sshCredentialId = sshCredentialId;
    }

    @DataBoundSetter
    public void setUsername(String username) {
        this.username = username;
    }

    @DataBoundSetter
    public void setPort(int port) {
        this.port = port;
    }

    @DataBoundSetter
    public void setSshConfig(SshConfig sshConfig) {
        this.sshConfig = sshConfig;
    }

    /** Display name for UI dropdowns */
    public String getDisplayName() {
        return name + " (" + getHosts().size() + " hosts)";
    }

    /** Check if this environment is properly configured */
    public boolean isValid() {
        return name != null
                && !name.trim().isEmpty()
                && !getHosts().isEmpty()
                && sshCredentialId != null
                && !sshCredentialId.trim().isEmpty();
    }

    /** Get configuration summary for logging */
    public String getConfigSummary() {
        return String.format(
                "Environment '%s': %d hosts, user=%s, port=%d, credential=%s, %s",
                name,
                getHosts().size(),
                username,
                port,
                sshCredentialId,
                getSshConfig().getSummary());
    }

    @Override
    public String toString() {
        return "SshEnvironment{"
                + "name='"
                + name
                + '\''
                + ", hosts="
                + hosts
                + ", sshCredentialId='"
                + sshCredentialId
                + '\''
                + ", username='"
                + username
                + '\''
                + ", port="
                + port
                + ", sshConfig="
                + sshConfig
                + '}';
    }
}
