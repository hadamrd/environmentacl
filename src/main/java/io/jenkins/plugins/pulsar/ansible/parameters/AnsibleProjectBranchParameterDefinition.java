package io.jenkins.plugins.pulsar.ansible.parameters;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.StringParameterValue;
import io.jenkins.plugins.pulsar.ansible.AnsibleProjectsGlobalConfiguration;
import io.jenkins.plugins.pulsar.ansible.model.AnsibleProject;
import jenkins.model.Jenkins;
import org.eclipse.jgit.lib.ObjectId;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest2;


public class AnsibleProjectBranchParameterDefinition extends ParameterDefinition {
    private static final Logger LOGGER = Logger.getLogger(AnsibleProjectBranchParameterDefinition.class.getName());

    private final String projectId;

    @DataBoundConstructor
    public AnsibleProjectBranchParameterDefinition(String name, String description, String projectId) {
        super(name);
        setDescription(description);
        this.projectId = projectId;
    }

    public String getProjectId() {
        return projectId;
    }

    public List<String> getChoices() {
        try {
            LOGGER.info("Fetching branches for project: " + projectId);
            
            // Get the ansible project
            AnsibleProjectsGlobalConfiguration config = AnsibleProjectsGlobalConfiguration.get();
            AnsibleProject project = config.getProjectById(projectId);
            
            if (project == null) {
                LOGGER.warning("Project not found: " + projectId);
                return Arrays.asList("Project not found");
            }

            String repoUrl = project.getRepository();
            if (repoUrl == null || repoUrl.trim().isEmpty()) {
                LOGGER.warning("No repository URL for project: " + projectId);
                return Arrays.asList("No repository configured");
            }

            LOGGER.info("Getting branches from: " + repoUrl);

            // Get branches using Git client
            List<String> branches = new ArrayList<>();
            GitClient gitClient = Git.with(null, null).getClient();
            Map<String, ObjectId> refs = gitClient.getRemoteReferences(repoUrl, null, true, false);
            
            for (String ref : refs.keySet()) {
                if (ref.startsWith("refs/heads/")) {
                    branches.add(ref.substring("refs/heads/".length()));
                }
            }

            LOGGER.info("Found " + branches.size() + " branches");
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
    @Symbol("ansibleProjectBranch")
    public static class DescriptorImpl extends ParameterDefinition.ParameterDescriptor {
        @Override
        public String getDisplayName() {
            return "Ansible Project Branch Parameter";
        }
    }
}