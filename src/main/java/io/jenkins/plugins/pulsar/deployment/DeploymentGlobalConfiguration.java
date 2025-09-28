package io.jenkins.plugins.pulsar.deployment;

import hudson.Extension;
import io.jenkins.plugins.pulsar.deployment.model.DeploymentJob;
import io.jenkins.plugins.pulsar.deployment.model.DeploymentTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import jenkins.model.GlobalConfiguration;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundSetter;

@Extension
@Symbol("pulsarDeployments")
public class DeploymentGlobalConfiguration extends GlobalConfiguration {

    private List<DeploymentJob> jobs = new ArrayList<>();
    private List<DeploymentTemplate> templates = new ArrayList<>();

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

    public List<DeploymentTemplate> getTemplates() {
        return templates;
    }

    @DataBoundSetter
    public void setJobs(List<DeploymentJob> components) {
        this.jobs = components != null ? components : new ArrayList<>();
        save();
    }

    @DataBoundSetter
    public void setTemplates(List<DeploymentTemplate> templates) {
        this.templates = templates != null ? templates : new ArrayList<>();
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
        return getJobs().stream().map(DeploymentJob::getId).collect(Collectors.toList());
    }

    public List<String> getAllCategories() {
        return getJobs().stream().map(DeploymentJob::getCategory).distinct().collect(Collectors.toList());
    }

    /** Find template by name */
    public DeploymentTemplate getTemplate(String name) {
        return templates.stream()
                .filter(template -> name.equals(template.getName()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public String getDisplayName() {
        return "Deployment Components Configuration";
    }
}
