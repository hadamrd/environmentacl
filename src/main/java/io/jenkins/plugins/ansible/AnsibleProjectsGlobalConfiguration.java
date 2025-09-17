package io.jenkins.plugins.ansible;

import hudson.Extension;
import io.jenkins.plugins.ansible.model.AnsibleProject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

@Extension
@Symbol("ansibleProjects")
public class AnsibleProjectsGlobalConfiguration extends GlobalConfiguration {
    private List<AnsibleProject> projects = new ArrayList<>();

    public AnsibleProjectsGlobalConfiguration() {
        load();
    }

    public static AnsibleProjectsGlobalConfiguration get() {
        return GlobalConfiguration.all().get(AnsibleProjectsGlobalConfiguration.class);
    }

    // Projects
    public List<AnsibleProject> getProjects() {
        return projects != null ? projects : new ArrayList<>();
    }

    @DataBoundSetter
    public void setProjects(List<AnsibleProject> projects) {
        this.projects = projects != null ? projects : new ArrayList<>();
        save();
    }

    // Utility methods
    public AnsibleProject getProjectById(String projectId) {
        return getProjects().stream()
                .filter(project -> projectId.equals(project.getId()))
                .findFirst()
                .orElse(null);
    }

    public List<String> getAllProjectIds() {
        return getProjects().stream()
                .map(AnsibleProject::getId)
                .collect(Collectors.toList());
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        req.bindJSON(this, json);
        save();
        return true;
    }
}