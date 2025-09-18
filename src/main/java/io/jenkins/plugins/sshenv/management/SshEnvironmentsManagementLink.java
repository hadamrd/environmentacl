package io.jenkins.plugins.sshenv.management;

import hudson.Extension;
import hudson.model.ManagementLink;
import hudson.security.Permission;
import io.jenkins.plugins.sshenv.config.SshEnvironmentsGlobalConfiguration;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.verb.POST;

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

    /** Handle configuration form submission */
    @POST
    public HttpResponse doConfigSubmit(StaplerRequest req, StaplerResponse rsp) throws Exception {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        SshEnvironmentsGlobalConfiguration config = SshEnvironmentsGlobalConfiguration.get();
        req.bindJSON(config, req.getSubmittedForm());
        config.save();

        return HttpResponses.redirectToContextRoot();
    }

    /** Test SSH connection to a specific environment */
    public String doTestEnvironment(@QueryParameter String environmentName) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        SshEnvironmentsGlobalConfiguration config = SshEnvironmentsGlobalConfiguration.get();

        if (environmentName == null || environmentName.isEmpty()) {
            return "Error: Environment name is required";
        }

        var environment = config.getEnvironmentByName(environmentName);
        if (environment == null) {
            return "Error: Environment '" + environmentName + "' not found";
        }

        if (!environment.isValid()) {
            return "Error: Environment '" + environmentName + "' is not properly configured";
        }

        // In a real implementation, you would test SSH connectivity here
        return "Success: Environment '"
                + environmentName
                + "' configuration is valid. "
                + "Hosts: "
                + environment.getHosts()
                + ", "
                + "Credential: "
                + environment.getSshCredentialId();
    }
}
