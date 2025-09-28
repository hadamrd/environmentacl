package io.jenkins.plugins.pulsar.environment;

import hudson.Extension;
import io.jenkins.plugins.pulsar.environment.model.ACLRule;
import io.jenkins.plugins.pulsar.environment.model.EnvironmentGroup;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import jenkins.model.GlobalConfiguration;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundSetter;

@Extension
@Symbol("environmentACL")
public class EnvironmentACLGlobalConfiguration extends GlobalConfiguration {
    private List<EnvironmentGroup> environmentGroups = new ArrayList<>();
    private List<ACLRule> aclRules = new ArrayList<>();

    public EnvironmentACLGlobalConfiguration() {
        load();
    }

    public static EnvironmentACLGlobalConfiguration get() {
        return GlobalConfiguration.all().get(EnvironmentACLGlobalConfiguration.class);
    }

    // Environment Groups
    public List<EnvironmentGroup> getEnvironmentGroups() {
        return environmentGroups != null ? environmentGroups : new ArrayList<>();
    }

    @DataBoundSetter
    public void setEnvironmentGroups(List<EnvironmentGroup> environmentGroups) {
        this.environmentGroups = environmentGroups != null ? environmentGroups : new ArrayList<>();
        save();
    }

    // ACL Rules
    public List<ACLRule> getAclRules() {
        return aclRules != null ? aclRules : new ArrayList<>();
    }

    @DataBoundSetter
    public void setAclRules(List<ACLRule> aclRules) {
        this.aclRules = aclRules != null ? aclRules : new ArrayList<>();
        save();
    }

    public List<String> getAllEnvironments() {
        return getEnvironmentGroups().stream()
                .flatMap(group -> group.getEnvironments().stream())
                .distinct()
                .collect(Collectors.toList());
    }

    public EnvironmentGroup getEnvironmentGroupForEnvironment(String environment) {
        return getEnvironmentGroups().stream()
                .filter(group -> group.getEnvironments().contains(environment))
                .findFirst()
                .orElse(null);
    }

    public String getVaultCredentialId(String environment, String vaultId) {
        EnvironmentGroup group = getEnvironmentGroupForEnvironment(environment);
        return group != null ? group.getVaultCredentialId(vaultId) : null;
    }

    public String getNodeLabelsForEnvironment(String environment) {
        EnvironmentGroup group = getEnvironmentGroupForEnvironment(environment);
        if (group == null) {
            throw new RuntimeException("Group is not mapped to any node labels!");
        }
        return group.getNodeLabelsAsString();
    }

    public List<String> getNodeLabelsListForEnvironment(String environment) {
        EnvironmentGroup group = getEnvironmentGroupForEnvironment(environment);
        if (group == null) {
            throw new RuntimeException("Group is not mapped to any node labels!");
        }
        return group.getNodeLabels();
    }
}