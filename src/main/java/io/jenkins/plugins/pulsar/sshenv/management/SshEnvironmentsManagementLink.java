package io.jenkins.plugins.pulsar.sshenv.management;

import hudson.Extension;
import hudson.model.ManagementLink;
import hudson.security.Permission;
import io.jenkins.plugins.pulsar.sshenv.config.SshEnvironmentsGlobalConfiguration;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;

/**
 * Management link for SSH Environments configuration. Provides a dedicated page for managing SSH
 * environments.
 */
@Extension
@Symbol("sshEnvironmentsManagement")
public class SshEnvironmentsManagementLink extends ManagementLink {

    @Override
    public String getUrlName() {
        return "ssh-environments";
    }

    @Override
    public String getDisplayName() {
        return "SSH Environments";
    }

    @Override
    public String getDescription() {
        return "Configure SSH environments for remote command execution";
    }

    @Override
    public String getIconFileName() {
        return "symbol-server-outline plugin-ionicons-api";
    }

    @Override
    public Category getCategory() {
        return Category.CONFIGURATION;
    }

    @Override
    public Permission getRequiredPermission() {
        return Jenkins.ADMINISTER;
    }

    /** Get the current SSH environments configuration */
    public SshEnvironmentsGlobalConfiguration getConfiguration() {
        return SshEnvironmentsGlobalConfiguration.get();
    }
}
