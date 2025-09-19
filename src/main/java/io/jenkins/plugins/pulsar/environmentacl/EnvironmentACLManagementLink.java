package io.jenkins.plugins.pulsar.environmentacl;

import hudson.Extension;
import hudson.model.ManagementLink;

@Extension
public class EnvironmentACLManagementLink extends ManagementLink {

    @Override
    public String getIconFileName() {
        return "symbol-shield";
    }

    @Override
    public String getDisplayName() {
        return "Environment ACL Manager";
    }

    @Override
    public String getUrlName() {
        return "environment-acl";
    }

    @Override
    public String getDescription() {
        return "Manage environment groups and access control rules";
    }

    public EnvironmentACLGlobalConfiguration getConfiguration() {
        return EnvironmentACLGlobalConfiguration.get();
    }

    // This makes the management link appear in Manage Jenkins
    @Override
    public Category getCategory() {
        return Category.SECURITY;
    }

    // Check if user has permission to access this page
    @Override
    public boolean getRequiresConfirmation() {
        return false;
    }
}
