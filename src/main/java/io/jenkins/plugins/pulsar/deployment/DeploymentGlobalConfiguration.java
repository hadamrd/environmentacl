package io.jenkins.plugins.pulsar.deployment;

import hudson.Extension;
import io.jenkins.plugins.pulsar.deployment.model.DeploymentJob;
import jenkins.model.GlobalConfiguration;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Extension
@Symbol("deploymentJobs")
public class DeploymentGlobalConfiguration extends GlobalConfiguration {
    
    private List<DeploymentJob> jobs = new ArrayList<>();

    public DeploymentGlobalConfiguration() {
        load();
    }

    public static DeploymentGlobalConfiguration get() {
        return GlobalConfiguration.all().get(DeploymentGlobalConfiguration.class);
    }

    // Components management
    public List<DeploymentJob> getJobs() {
        return jobs != null ? jobs : new ArrayList<>();
    }

    @DataBoundSetter
    public void setJobs(List<DeploymentJob> components) {
        this.jobs = components != null ? components : new ArrayList<>();
        save();
    }

    // Utility methods
    public DeploymentJob getComponentById(String componentId) {
        return getJobs().stream()
                .filter(component -> componentId.equals(component.getId()))
                .findFirst()
                .orElse(null);
    }

    public List<DeploymentJob> getComponentsByCategory(String category) {
        return getJobs().stream()
                .filter(component -> category.equals(component.getCategory()))
                .collect(Collectors.toList());
    }

    public List<String> getAllComponentIds() {
        return getJobs().stream()
                .map(DeploymentJob::getId)
                .collect(Collectors.toList());
    }

    public List<String> getAllCategories() {
        return getJobs().stream()
                .map(DeploymentJob::getCategory)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public String getDisplayName() {
        return "Deployment Components Configuration";
    }
}