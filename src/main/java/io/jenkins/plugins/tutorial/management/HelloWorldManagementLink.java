package io.jenkins.plugins.tutorial.management;

import hudson.Extension;
import hudson.model.ManagementLink;
import hudson.security.Permission;
import io.jenkins.plugins.tutorial.config.HelloWorldGlobalConfiguration;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.verb.POST;

/**
 * Management link that appears in "Manage Jenkins" section. Provides a dedicated page for
 * HelloWorld plugin configuration.
 */
@Extension
@Symbol("helloWorldManagement")
public class HelloWorldManagementLink extends ManagementLink {

    /** URL segment for this management page. Will be accessible at: /manage/helloworld */
    @Override
    public String getUrlName() {
        return "helloworld";
    }

    /** Display name shown in the Manage Jenkins page */
    @Override
    public String getDisplayName() {
        return "HelloWorld Configuration";
    }

    /** Description shown under the link in Manage Jenkins */
    @Override
    public String getDescription() {
        return "Configure HelloWorld plugin settings and greetings";
    }

    /**
     * Icon name (from Jenkins icon set) or path to custom icon. Jenkins provides many built-in icons
     * like 'gear', 'folder', 'document', etc.
     */
    @Override
    public String getIconFileName() {
        return "symbol-chat-outline plugin-ionicons-api"; // Modern Jenkins icon
    }

    /**
     * Category for grouping in Manage Jenkins page. Common categories: "CONFIGURATION", "SECURITY",
     * "STATUS", "TROUBLESHOOTING", "TOOLS"
     */
    @Override
    public Category getCategory() {
        return Category.CONFIGURATION;
    }

    /**
     * Permission required to access this management page. ADMINISTER = only Jenkins admins can access
     */
    @Override
    public Permission getRequiredPermission() {
        return Jenkins.ADMINISTER;
    }

    /** Get the current configuration instance for the Jelly template */
    public HelloWorldGlobalConfiguration getConfiguration() {
        return HelloWorldGlobalConfiguration.get();
    }

    /**
     * Handle form submission from the management page. This method is called when users submit the
     * configuration form.
     */
    @POST
    public HttpResponse doConfigSubmit(StaplerRequest req, StaplerResponse rsp) throws Exception {
        // Check permissions
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        // Get the configuration instance
        HelloWorldGlobalConfiguration config = HelloWorldGlobalConfiguration.get();

        // Bind form data to the configuration object
        req.bindJSON(config, req.getSubmittedForm());

        // Save the configuration
        config.save();

        // Redirect back to the management page with success message
        return HttpResponses.redirectToContextRoot();
    }

    /**
     * Test action to demonstrate AJAX calls from the UI. This can be called via JavaScript:
     * /manage/helloworld/testGreeting?message=test
     */
    public String doTestGreeting(@QueryParameter String message) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        HelloWorldGlobalConfiguration config = HelloWorldGlobalConfiguration.get();

        if (message == null || message.isEmpty()) {
            message = config.getDefaultMessage();
        }

        return "Test greeting: " + message;
    }
}
