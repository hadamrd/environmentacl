package io.jenkins.plugins.tutorial.config;

import hudson.Extension;
import io.jenkins.plugins.tutorial.model.Greeting;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Global configuration for HelloWorld plugin.
 * This class manages plugin-wide settings that can be configured via:
 * - Jenkins UI (Manage Jenkins > Configure System)
 * - Jenkins Configuration as Code (JCasC)
 */
@Extension
@Symbol("helloWorld")  // This enables JCasC support - users can configure via YAML
public class HelloWorldGlobalConfiguration extends GlobalConfiguration {
    
    // Default greeting message
    private String defaultMessage = "Hello, World!";
    
    // Whether to include timestamps in greetings
    private boolean includeTimestamp = false;
    
    // List of predefined greetings
    private List<Greeting> greetings = new ArrayList<>();

    /**
     * Constructor - loads existing configuration from disk
     */
    public HelloWorldGlobalConfiguration() {
        load();  // Load saved configuration from Jenkins home
    }

    /**
     * Static method to get the current global configuration instance.
     * This is the recommended way to access global config from other classes.
     */
    public static HelloWorldGlobalConfiguration get() {
        return GlobalConfiguration.all().get(HelloWorldGlobalConfiguration.class);
    }

    // Getters and setters for configuration properties

    public String getDefaultMessage() {
        return defaultMessage;
    }

    /**
     * @DataBoundSetter enables this property to be set via:
     * - Jenkins UI forms
     * - JCasC YAML configuration
     * - REST API calls
     */
    @DataBoundSetter
    public void setDefaultMessage(String defaultMessage) {
        this.defaultMessage = defaultMessage;
        save();  // Persist changes to disk
    }

    public boolean isIncludeTimestamp() {
        return includeTimestamp;
    }

    @DataBoundSetter
    public void setIncludeTimestamp(boolean includeTimestamp) {
        this.includeTimestamp = includeTimestamp;
        save();
    }

    public List<Greeting> getGreetings() {
        return greetings != null ? greetings : new ArrayList<>();
    }

    @DataBoundSetter
    public void setGreetings(List<Greeting> greetings) {
        this.greetings = greetings != null ? greetings : new ArrayList<>();
        save();
    }

    // Utility methods for other plugin components to use

    /**
     * Get a greeting by its ID
     */
    public Greeting getGreetingById(String greetingId) {
        return getGreetings().stream()
                .filter(greeting -> greetingId.equals(greeting.getId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get all greeting IDs for dropdowns/validation
     */
    public List<String> getAllGreetingIds() {
        return getGreetings().stream()
                .map(Greeting::getId)
                .collect(Collectors.toList());
    }

    /**
     * Get a random greeting message
     */
    public String getRandomGreeting() {
        List<Greeting> allGreetings = getGreetings();
        if (allGreetings.isEmpty()) {
            return getDefaultMessage();
        }
        int randomIndex = (int) (Math.random() * allGreetings.size());
        return allGreetings.get(randomIndex).getMessage();
    }

    /**
     * Called when configuration is submitted via Jenkins UI.
     * This handles form submission and saves the configuration.
     */
    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        // Bind JSON data from the form to this object's properties
        req.bindJSON(this, json);
        save();  // Persist to disk
        return true;
    }
}