package io.jenkins.plugins.environmentacl.model;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import java.util.ArrayList;
import java.util.List;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class ACLRule extends AbstractDescribableImpl<ACLRule> {
    private String name;
    private String type; // "allow" or "deny"
    private int priority = 0;
    private List<String> jobs;
    private List<String> environments;
    private List<String> environmentGroups;
    private List<String> environmentTags;
    private List<String> users;
    private List<String> groups;

    @DataBoundConstructor
    public ACLRule(String name, String type) {
        this.name = name;
        this.type = type;
        this.jobs = new ArrayList<>();
        this.environments = new ArrayList<>();
        this.environmentGroups = new ArrayList<>();
        this.environmentTags = new ArrayList<>();
        this.users = new ArrayList<>();
        this.groups = new ArrayList<>();
    }

    // Getters
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
        return jobs != null ? jobs : new ArrayList<>();
    }

    public List<String> getEnvironments() {
        return environments != null ? environments : new ArrayList<>();
    }

    public List<String> getEnvironmentGroups() {
        return environmentGroups != null ? environmentGroups : new ArrayList<>();
    }

    public List<String> getEnvironmentTags() {
        return environmentTags != null ? environmentTags : new ArrayList<>();
    }

    public List<String> getUsers() {
        return users != null ? users : new ArrayList<>();
    }

    public List<String> getGroups() {
        return groups != null ? groups : new ArrayList<>();
    }

    // Setters
    @DataBoundSetter
    public void setPriority(int priority) {
        this.priority = priority;
    }

    @DataBoundSetter
    public void setJobs(List<String> jobs) {
        this.jobs = jobs != null ? jobs : new ArrayList<>();
    }

    @DataBoundSetter
    public void setEnvironments(List<String> environments) {
        this.environments = environments != null ? environments : new ArrayList<>();
    }

    @DataBoundSetter
    public void setEnvironmentGroups(List<String> environmentGroups) {
        this.environmentGroups = environmentGroups != null ? environmentGroups : new ArrayList<>();
    }

    @DataBoundSetter
    public void setEnvironmentTags(List<String> environmentTags) {
        this.environmentTags = environmentTags != null ? environmentTags : new ArrayList<>();
    }

    @DataBoundSetter
    public void setUsers(List<String> users) {
        this.users = users != null ? users : new ArrayList<>();
    }

    @DataBoundSetter
    public void setGroups(List<String> groups) {
        this.groups = groups != null ? groups : new ArrayList<>();
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<ACLRule> {
        @Override
        public String getDisplayName() {
            return "ACL Rule";
        }
    }
}
