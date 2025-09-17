package io.jenkins.plugins.ansible.model;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class AnsibleConfig extends AbstractDescribableImpl<AnsibleConfig> {

    // [defaults] section
    private String inventory;
    private String library;
    private String moduleUtils;
    private String remoteUser;
    private String askSudoPass;
    private String askPass;
    private String transport;
    private String remotePort;
    private String moduleLanguage;
    private String moduleLang;
    private String gathering;
    private String gatherSubset;
    private String gatherTimeout;
    private String factCaching;
    private String factCachingConnection;
    private String factCachingTimeout;
    private String stdout_callback;
    private String callbackWhitelist;
    private String taskIncludes_static;
    private String handlerIncludes_static;
    private String errorOnUndefinedVars;
    private String systemWarnings;
    private String deprecationWarnings;
    private String commandWarnings;
    private String bin_ansible_callbacks;
    private String nocows;
    private String nocolor;
    private String logPath;

    // [inventory] section
    private String enablePlugins;
    private String ignoreExtensions;
    private String ignorePatternsExtensions;

    // [privilege_escalation] section
    private String become;
    private String becomeMethod;
    private String becomeUser;
    private String becomeAskPass;

    // [paramiko_connection] section
    private String recordHostKeys;
    private String proxyCommand;

    // [ssh_connection] section
    private String sshArgs;
    private String controlPath;
    private String controlMaster;
    private String controlPersist;
    private String pipelining;
    private String timeout;
    private String retries;
    private String hostKeyChecking;
    private String scpIfSsh;
    private String sftp_batch_mode;
    private String uucp;

    // [persistent_connection] section
    private String connectTimeout;
    private String connectRetryTimeout;
    private String connectInterval;

    // [accelerate] section
    private String acceleratePort;
    private String accelerateTimeout;
    private String accelerateConnectTimeout;
    private String accelerateDaemonTimeout;
    private String accelerateMultiKey;

    // [selinux] section
    private String libvirtLxcNoseclabel;

    // [colors] section
    private String highlight;
    private String verbose;
    private String warn;
    private String error;
    private String debug;
    private String deprecate;
    private String skip;
    private String unreachable;
    private String ok;
    private String changed;
    private String diffAdd;
    private String diffRemove;
    private String diffLines;

    @DataBoundConstructor
    public AnsibleConfig() {
        // Set common defaults
        this.hostKeyChecking = "False";
        this.gathering = "implicit";
        this.timeout = "10";
        this.retries = "3";
        this.pipelining = "False";
        this.transport = "smart";
        this.stdout_callback = "default";
        this.nocows = "1";
    }

    // Most commonly used getters/setters

    public String getHostKeyChecking() {
        return hostKeyChecking;
    }

    @DataBoundSetter
    public void setHostKeyChecking(String hostKeyChecking) {
        this.hostKeyChecking = hostKeyChecking;
    }

    public String getTimeout() {
        return timeout;
    }

    @DataBoundSetter
    public void setTimeout(String timeout) {
        this.timeout = timeout;
    }

    public String getRetries() {
        return retries;
    }

    @DataBoundSetter
    public void setRetries(String retries) {
        this.retries = retries;
    }

    public String getGathering() {
        return gathering;
    }

    @DataBoundSetter
    public void setGathering(String gathering) {
        this.gathering = gathering;
    }

    public String getPipelining() {
        return pipelining;
    }

    @DataBoundSetter
    public void setPipelining(String pipelining) {
        this.pipelining = pipelining;
    }

    public String getInventory() {
        return inventory;
    }

    @DataBoundSetter
    public void setInventory(String inventory) {
        this.inventory = inventory;
    }

    public String getLibrary() {
        return library;
    }

    @DataBoundSetter
    public void setLibrary(String library) {
        this.library = library;
    }

    public String getRemoteUser() {
        return remoteUser;
    }

    @DataBoundSetter
    public void setRemoteUser(String remoteUser) {
        this.remoteUser = remoteUser;
    }

    public String getTransport() {
        return transport;
    }

    @DataBoundSetter
    public void setTransport(String transport) {
        this.transport = transport;
    }

    public String getRemotePort() {
        return remotePort;
    }

    @DataBoundSetter
    public void setRemotePort(String remotePort) {
        this.remotePort = remotePort;
    }

    public String getStdout_callback() {
        return stdout_callback;
    }

    @DataBoundSetter
    public void setStdout_callback(String stdout_callback) {
        this.stdout_callback = stdout_callback;
    }

    public String getCallbackWhitelist() {
        return callbackWhitelist;
    }

    @DataBoundSetter
    public void setCallbackWhitelist(String callbackWhitelist) {
        this.callbackWhitelist = callbackWhitelist;
    }

    public String getLogPath() {
        return logPath;
    }

    @DataBoundSetter
    public void setLogPath(String logPath) {
        this.logPath = logPath;
    }

    public String getNocows() {
        return nocows;
    }

    @DataBoundSetter
    public void setNocows(String nocows) {
        this.nocows = nocows;
    }

    public String getNocolor() {
        return nocolor;
    }

    @DataBoundSetter
    public void setNocolor(String nocolor) {
        this.nocolor = nocolor;
    }

    // Privilege escalation
    public String getBecome() {
        return become;
    }

    @DataBoundSetter
    public void setBecome(String become) {
        this.become = become;
    }

    public String getBecomeMethod() {
        return becomeMethod;
    }

    @DataBoundSetter
    public void setBecomeMethod(String becomeMethod) {
        this.becomeMethod = becomeMethod;
    }

    public String getBecomeUser() {
        return becomeUser;
    }

    @DataBoundSetter
    public void setBecomeUser(String becomeUser) {
        this.becomeUser = becomeUser;
    }

    // SSH Connection
    public String getSshArgs() {
        return sshArgs;
    }

    @DataBoundSetter
    public void setSshArgs(String sshArgs) {
        this.sshArgs = sshArgs;
    }

    public String getControlPath() {
        return controlPath;
    }

    @DataBoundSetter
    public void setControlPath(String controlPath) {
        this.controlPath = controlPath;
    }

    public String getControlMaster() {
        return controlMaster;
    }

    @DataBoundSetter
    public void setControlMaster(String controlMaster) {
        this.controlMaster = controlMaster;
    }

    public String getControlPersist() {
        return controlPersist;
    }

    @DataBoundSetter
    public void setControlPersist(String controlPersist) {
        this.controlPersist = controlPersist;
    }

    // Fact gathering
    public String getFactCaching() {
        return factCaching;
    }

    @DataBoundSetter
    public void setFactCaching(String factCaching) {
        this.factCaching = factCaching;
    }

    public String getFactCachingConnection() {
        return factCachingConnection;
    }

    @DataBoundSetter
    public void setFactCachingConnection(String factCachingConnection) {
        this.factCachingConnection = factCachingConnection;
    }

    public String getFactCachingTimeout() {
        return factCachingTimeout;
    }

    @DataBoundSetter
    public void setFactCachingTimeout(String factCachingTimeout) {
        this.factCachingTimeout = factCachingTimeout;
    }

    public String getGatherTimeout() {
        return gatherTimeout;
    }

    @DataBoundSetter
    public void setGatherTimeout(String gatherTimeout) {
        this.gatherTimeout = gatherTimeout;
    }

    // Error handling
    public String getErrorOnUndefinedVars() {
        return errorOnUndefinedVars;
    }

    @DataBoundSetter
    public void setErrorOnUndefinedVars(String errorOnUndefinedVars) {
        this.errorOnUndefinedVars = errorOnUndefinedVars;
    }

    // Warning controls
    public String getSystemWarnings() {
        return systemWarnings;
    }

    @DataBoundSetter
    public void setSystemWarnings(String systemWarnings) {
        this.systemWarnings = systemWarnings;
    }

    public String getDeprecationWarnings() {
        return deprecationWarnings;
    }

    @DataBoundSetter
    public void setDeprecationWarnings(String deprecationWarnings) {
        this.deprecationWarnings = deprecationWarnings;
    }

    public String getCommandWarnings() {
        return commandWarnings;
    }

    @DataBoundSetter
    public void setCommandWarnings(String commandWarnings) {
        this.commandWarnings = commandWarnings;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<AnsibleConfig> {
        @Override
        public String getDisplayName() {
            return "Ansible Configuration";
        }
    }
}
