package io.jenkins.plugins.pulsar.sshenv.config;

import hudson.Extension;
import io.jenkins.plugins.pulsar.sshenv.model.SshEnvironment;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Global configuration for SSH environments. Manages environments that can be configured via JCasC
 * or Jenkins UI.
 */
@Extension
@Symbol("sshEnvironments") // This enables JCasC support
public class SshEnvironmentsGlobalConfiguration extends GlobalConfiguration {

    // List of configured SSH environments
    private List<SshEnvironment> environments = new ArrayList<>();

    /** Constructor - loads existing configuration from disk */
    public SshEnvironmentsGlobalConfiguration() {
        load();
    }

    /** Static method to get the current global configuration instance. */
    public static SshEnvironmentsGlobalConfiguration get() {
        return GlobalConfiguration.all().get(SshEnvironmentsGlobalConfiguration.class);
    }

    // Configuration properties

    public List<SshEnvironment> getEnvironments() {
        return environments != null ? environments : new ArrayList<>();
    }

    @DataBoundSetter
    public void setEnvironments(List<SshEnvironment> environments) {
        this.environments = environments != null ? environments : new ArrayList<>();
        save();
    }

    // Utility methods for pipeline steps

    /** Get an environment by name */
    public SshEnvironment getEnvironmentByName(String name) {
        return getEnvironments().stream()
                .filter(env -> name.equals(env.getName()))
                .findFirst()
                .orElse(null);
    }

    /** Get all environment names for dropdowns */
    public List<String> getAllEnvironmentNames() {
        return getEnvironments().stream().map(SshEnvironment::getName).collect(Collectors.toList());
    }

    /** Get only valid environments (properly configured) */
    public List<SshEnvironment> getValidEnvironments() {
        return getEnvironments().stream().filter(SshEnvironment::isValid).collect(Collectors.toList());
    }

    /** Check if an environment exists and is valid */
    public boolean isEnvironmentValid(String name) {
        SshEnvironment env = getEnvironmentByName(name);
        return env != null && env.isValid();
    }

    /** Get environment that contains the specified host */
    public SshEnvironment getEnvironmentByHost(String host) {
        return getEnvironments().stream()
                .filter(env -> env.getHosts().contains(host))
                .findFirst()
                .orElse(null);
    }
}
