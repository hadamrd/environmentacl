package io.jenkins.plugins.environmentacl;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import java.util.List;
import java.util.Objects;
import org.kohsuke.stapler.DataBoundConstructor;

/** Represents an ACL rule for controlling access to jobs and environments */
public class ACLRule extends AbstractDescribableImpl<ACLRule> {

    private final String name;
    private final String type; // allow or deny
    private final int priority;
    private final List<String> jobs;
    private final List<String> environments;
    private final List<String> envCategories;
    private final List<String> users;
    private final List<String> groups;

    @DataBoundConstructor
    public ACLRule(
            String name,
            String type,
            int priority,
            List<String> jobs,
            List<String> environments,
            List<String> envCategories,
            List<String> users,
            List<String> groups) {
        this.name = name;
        this.type = type;
        this.priority = priority;
        this.jobs = jobs;
        this.environments = environments;
        this.envCategories = envCategories;
        this.users = users;
        this.groups = groups;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public int getPriority() {
        return priority;
    }

    public List<String> getJobs() {
        return jobs;
    }

    public List<String> getEnvironments() {
        return environments;
    }

    public List<String> getEnvCategories() {
        return envCategories;
    }

    public List<String> getUsers() {
        return users;
    }

    public List<String> getGroups() {
        return groups;
    }

    public boolean isAllow() {
        return "allow".equalsIgnoreCase(type);
    }

    public boolean isDeny() {
        return "deny".equalsIgnoreCase(type);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ACLRule aclRule = (ACLRule) o;
        return priority == aclRule.priority && Objects.equals(name, aclRule.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, priority);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<ACLRule> {
        @Override
        public String getDisplayName() {
            return "ACL Rule";
        }
    }
}
