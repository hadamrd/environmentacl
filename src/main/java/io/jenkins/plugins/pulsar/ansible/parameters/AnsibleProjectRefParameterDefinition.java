package io.jenkins.plugins.pulsar.ansible.parameters;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.StringParameterValue;
import io.jenkins.plugins.pulsar.ansible.AnsibleProjectsGlobalConfiguration;
import io.jenkins.plugins.pulsar.ansible.model.AnsibleProject;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.json.JSONObject;
import org.eclipse.jgit.lib.ObjectId;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest2;

public class AnsibleProjectRefParameterDefinition extends ParameterDefinition {
    private static final Logger LOGGER = Logger.getLogger(AnsibleProjectRefParameterDefinition.class.getName());

    private String projectId;

    @DataBoundConstructor
    public AnsibleProjectRefParameterDefinition(String name, String description, String projectId) {
        super(name);
        setDescription(description);
        this.projectId = projectId;
    }

    public String getProjectId() {
        return projectId;
    }

    @DataBoundSetter
    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public List<String> getChoices() {
        try {
            LOGGER.log(Level.INFO, "Fetching branches for project: {0}", projectId);

            // Get the ansible project
            AnsibleProjectsGlobalConfiguration config = AnsibleProjectsGlobalConfiguration.get();
            AnsibleProject project = config.getProjectById(projectId);

            if (project == null) {
                LOGGER.log(Level.WARNING, "Project not found: {0}", projectId);
                return Arrays.asList("Project not found");
            }

            String repoUrl = project.getRepository();
            if (repoUrl == null || repoUrl.trim().isEmpty()) {
                LOGGER.log(Level.WARNING, "No repository URL for project: {0}", projectId);
                return Arrays.asList("No repository configured");
            }

            LOGGER.log(Level.INFO, "Getting branches from: {0}", repoUrl);

            // Get branches using Git client
            List<String> branches = new ArrayList<>();
            GitClient gitClient = Git.with(null, null).getClient();
            Map<String, ObjectId> refs = gitClient.getRemoteReferences(repoUrl, null, true, false);

            for (String ref : refs.keySet()) {
                if (ref.startsWith("refs/heads/")) {
                    branches.add(ref.substring("refs/heads/".length()));
                }
            }

            LOGGER.log(Level.INFO, "Found {0} branches", branches.size());
            return branches;

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting branches: " + e.getMessage(), e);
            return Arrays.asList("Error: " + e.getMessage());
        }
    }

    @Override
    public ParameterValue createValue(StaplerRequest2 req, JSONObject jo) {
        String value = jo.optString("value", "");
        return new StringParameterValue(getName(), value, getDescription());
    }

    @Override
    public ParameterValue getDefaultParameterValue() {
        List<String> choices = getChoices();
        String defaultValue = choices.isEmpty() ? "" : choices.get(0);
        return new StringParameterValue(getName(), defaultValue, getDescription());
    }

    @Extension
    @Symbol("ansibleProjectRef")
    public static class DescriptorImpl extends ParameterDefinition.ParameterDescriptor {
        @Override
        public String getDisplayName() {
            return "Ansible Project Branch Parameter";
        }
    }
}
