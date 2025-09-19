package io.jenkins.plugins.pulsar.sshenv.model;

import java.io.Serializable;
import java.util.Properties;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * SSH Configuration options for fine-tuning SSH connections. This allows per-environment SSH
 * configuration customization.
 */
public class SshConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    // Connection settings
    private int connectTimeout = 30000; // 30 seconds
    private int serverAliveInterval = 60; // 60 seconds
    private int serverAliveCountMax = 3; // 3 attempts

    // Security settings
    private boolean strictHostKeyChecking = false; // For demo - should be true in production
    private String preferredAuthentications = "publickey,keyboard-interactive,password";

    // Performance settings
    private boolean compressionEnabled = true;
    private int compressionLevel = 6;

    // Advanced settings
    private String cipherList = "aes128-ctr,aes192-ctr,aes256-ctr,aes128-cbc,3des-cbc";
    private String macList = "hmac-md5,hmac-sha1,hmac-sha2-256,hmac-sha1-96,hmac-md5-96";
    private String kexList =
            "diffie-hellman-group1-sha1,diffie-hellman-group14-sha1,diffie-hellman-group-exchange-sha1,diffie-hellman-group-exchange-sha256";

    /** Default constructor with sensible defaults */
    @DataBoundConstructor
    public SshConfig() {
        // Use defaults defined above
    }

    // Getters
    public int getConnectTimeout() {
        return connectTimeout;
    }

    public int getServerAliveInterval() {
        return serverAliveInterval;
    }

    public int getServerAliveCountMax() {
        return serverAliveCountMax;
    }

    public boolean isStrictHostKeyChecking() {
        return strictHostKeyChecking;
    }

    public String getPreferredAuthentications() {
        return preferredAuthentications;
    }

    public boolean isCompressionEnabled() {
        return compressionEnabled;
    }

    public int getCompressionLevel() {
        return compressionLevel;
    }

    public String getCipherList() {
        return cipherList;
    }

    public String getMacList() {
        return macList;
    }

    public String getKexList() {
        return kexList;
    }

    // Setters with @DataBoundSetter for UI binding
    @DataBoundSetter
    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = Math.max(5000, Math.min(connectTimeout, 300000)); // 5s to 5min
    }

    @DataBoundSetter
    public void setServerAliveInterval(int serverAliveInterval) {
        this.serverAliveInterval = Math.max(0, Math.min(serverAliveInterval, 3600)); // 0 to 1 hour
    }

    @DataBoundSetter
    public void setServerAliveCountMax(int serverAliveCountMax) {
        this.serverAliveCountMax = Math.max(1, Math.min(serverAliveCountMax, 10)); // 1 to 10
    }

    @DataBoundSetter
    public void setStrictHostKeyChecking(boolean strictHostKeyChecking) {
        this.strictHostKeyChecking = strictHostKeyChecking;
    }

    @DataBoundSetter
    public void setPreferredAuthentications(String preferredAuthentications) {
        this.preferredAuthentications = preferredAuthentications;
    }

    @DataBoundSetter
    public void setCompressionEnabled(boolean compressionEnabled) {
        this.compressionEnabled = compressionEnabled;
    }

    @DataBoundSetter
    public void setCompressionLevel(int compressionLevel) {
        this.compressionLevel = Math.max(1, Math.min(compressionLevel, 9)); // 1 to 9
    }

    @DataBoundSetter
    public void setCipherList(String cipherList) {
        this.cipherList = cipherList;
    }

    @DataBoundSetter
    public void setMacList(String macList) {
        this.macList = macList;
    }

    @DataBoundSetter
    public void setKexList(String kexList) {
        this.kexList = kexList;
    }

    /** Convert this configuration to JSch Properties object */
    public Properties toJSchProperties() {
        Properties props = new Properties();

        // Connection settings
        props.put("ConnectTimeout", String.valueOf(connectTimeout));
        if (serverAliveInterval > 0) {
            props.put("ServerAliveInterval", String.valueOf(serverAliveInterval));
            props.put("ServerAliveCountMax", String.valueOf(serverAliveCountMax));
        }

        // Security settings
        props.put("StrictHostKeyChecking", strictHostKeyChecking ? "yes" : "no");
        props.put("PreferredAuthentications", preferredAuthentications);

        // Compression
        if (compressionEnabled) {
            props.put("compression.s2c", "zlib@openssh.com,zlib,none");
            props.put("compression.c2s", "zlib@openssh.com,zlib,none");
            props.put("compression_level", String.valueOf(compressionLevel));
        } else {
            props.put("compression.s2c", "none");
            props.put("compression.c2s", "none");
        }

        // Advanced settings
        props.put("cipher.s2c", cipherList);
        props.put("cipher.c2s", cipherList);
        props.put("mac.s2c", macList);
        props.put("mac.c2s", macList);
        props.put("kex", kexList);

        return props;
    }

    /** Create a production-ready SSH configuration */
    public static SshConfig createProductionConfig() {
        SshConfig config = new SshConfig();
        config.setStrictHostKeyChecking(true); // Enable host key verification
        config.setConnectTimeout(10000); // Shorter timeout for production
        config.setPreferredAuthentications("publickey"); // Only use key-based auth
        config.setCompressionEnabled(false); // Disable compression for better performance
        return config;
    }

    /** Create a development-friendly SSH configuration */
    public static SshConfig createDevelopmentConfig() {
        SshConfig config = new SshConfig();
        config.setStrictHostKeyChecking(false); // Disable host key checking for dev
        config.setConnectTimeout(30000); // Longer timeout for dev environments
        config.setCompressionEnabled(true); // Enable compression for slower dev connections
        return config;
    }

    /** Get configuration summary for logging */
    public String getSummary() {
        return String.format(
                "SSH Config: timeout=%dms, keepalive=%ds, compression=%s, strict_host_checking=%s",
                connectTimeout,
                serverAliveInterval,
                compressionEnabled ? "on" : "off",
                strictHostKeyChecking ? "on" : "off");
    }

    @Override
    public String toString() {
        return "SshConfig{"
                + "connectTimeout="
                + connectTimeout
                + ", serverAliveInterval="
                + serverAliveInterval
                + ", serverAliveCountMax="
                + serverAliveCountMax
                + ", strictHostKeyChecking="
                + strictHostKeyChecking
                + ", preferredAuthentications='"
                + preferredAuthentications
                + '\''
                + ", compressionEnabled="
                + compressionEnabled
                + ", compressionLevel="
                + compressionLevel
                + '}';
    }
}
