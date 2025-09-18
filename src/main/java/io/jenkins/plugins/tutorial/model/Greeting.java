package io.jenkins.plugins.tutorial.model;

import java.io.Serializable;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Model class representing a greeting configuration. This is a simple data object that holds
 * greeting information.
 */
public class Greeting implements Serializable {
    private static final long serialVersionUID = 1L;

    // Required fields
    private final String id;
    private final String message;

    // Optional fields
    private String language = "en";
    private boolean enabled = true;

    /**
     * Constructor for required fields. @DataBoundConstructor tells Jenkins to use this constructor
     * when creating instances from form data or JCasC.
     */
    @DataBoundConstructor
    public Greeting(String id, String message) {
        this.id = id;
        this.message = message;
    }

    // Getters for all fields
    public String getId() {
        return id;
    }

    public String getMessage() {
        return message;
    }

    public String getLanguage() {
        return language;
    }

    public boolean isEnabled() {
        return enabled;
    }

    // Setters for optional fields
    @DataBoundSetter
    public void setLanguage(String language) {
        this.language = language;
    }

    @DataBoundSetter
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Display name for UI dropdowns */
    public String getDisplayName() {
        return id + " (" + language + ")";
    }

    @Override
    public String toString() {
        return "Greeting{"
                + "id='"
                + id
                + '\''
                + ", message='"
                + message
                + '\''
                + ", language='"
                + language
                + '\''
                + ", enabled="
                + enabled
                + '}';
    }
}
