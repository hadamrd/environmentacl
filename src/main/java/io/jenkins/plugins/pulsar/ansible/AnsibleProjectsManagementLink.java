package io.jenkins.plugins.pulsar.ansible;

import hudson.Extension;
import hudson.model.ManagementLink;

@Extension
public class AnsibleProjectsManagementLink extends ManagementLink {

    @Override
    public String getIconFileName() {
        return "symbol-gear-outline";
    }

    @Override
    public String getDisplayName() {
        return "Ansible Projects";
    }

    @Override
    public String getUrlName() {
        return "ansible-projects";
    }

    @Override
    public String getDescription() {
        return "Manage Ansible project configurations";
    }

    public AnsibleProjectsGlobalConfiguration getConfiguration() {
        return AnsibleProjectsGlobalConfiguration.get();
    }

    @Override
    public Category getCategory() {
        return Category.TOOLS;
    }

    @Override
    public boolean getRequiresConfirmation() {
        return false;
    }
}
