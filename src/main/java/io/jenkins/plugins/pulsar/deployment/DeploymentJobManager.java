package io.jenkins.plugins.pulsar.deployment;

import com.cloudbees.hudson.plugins.folder.Folder;
import hudson.model.Descriptor.FormException;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.TopLevelItem;
import io.jenkins.plugins.pulsar.deployment.model.DeploymentJob;
import io.jenkins.plugins.pulsar.deployment.model.DeploymentTemplate;
import io.jenkins.plugins.pulsar.environment.parameters.EnvironmentChoiceParameterDefinition;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.springframework.security.access.AccessDeniedException;

/** Manages the creation and updating of Jenkins jobs for deployment components */
public class DeploymentJobManager {

    private static final Logger LOGGER = Logger.getLogger(DeploymentJobManager.class.getName());
    private static final String ROOT_FOLDER_NAME = "projects";
    private static DeploymentJobManager instance;

    private DeploymentJobManager() {}

    public static synchronized DeploymentJobManager getInstance() {
        if (instance == null) {
            instance = new DeploymentJobManager();
        }
        return instance;
    }

    /** Update all jobs based on current component configuration */
    public void updateAllJobs() throws IOException, AccessDeniedException, InterruptedException, FormException {
        DeploymentGlobalConfiguration config = DeploymentGlobalConfiguration.get();

        // Ensure root folder exists
        Folder rootFolder = ensureRootFolder();

        // Get all components and organize by category
        List<DeploymentJob> components = config.getJobs();

        for (String category : config.getAllCategories()) {
            Folder categoryFolder = ensureCategoryFolder(rootFolder, category);
            List<DeploymentJob> categoryComponents = config.getComponentsByCategory(category);

            for (DeploymentJob component : categoryComponents) {
                createOrUpdateJob(categoryFolder, component);
            }
        }

        LOGGER.log(Level.INFO, "Successfully updated {0} component jobs", components.size());
    }

    public void createOrUpdateJob(Folder parent, DeploymentJob jobConfig)
            throws IOException, AccessDeniedException, InterruptedException, FormException {
        String jobName = "PulsarJob_" + jobConfig.getId();

        TopLevelItem existing = parent.getItem(jobName);
        WorkflowJob job;

        if (existing == null) {
            // Create if missing
            job = parent.createProject(WorkflowJob.class, jobName);
        } else if (existing instanceof WorkflowJob workflowJob) {
            job = workflowJob;
        } else {
            throw new IllegalStateException("Item " + jobName + " already exists but is not a WorkflowJob");
        }

        // Update metadata
        job.setDisplayName(jobConfig.getName());
        job.setDescription("Deploy component " + jobConfig.getId());

        // Replace parameters
        List<ParameterDefinition> parameters = createParameters(jobConfig);
        job.removeProperty(ParametersDefinitionProperty.class);
        if (!parameters.isEmpty()) {
            job.addProperty(new ParametersDefinitionProperty(parameters));
        }

        // Update pipeline
        String pipelineScript = generatePipelineScript(jobConfig);
        job.setDefinition(new CpsFlowDefinition(pipelineScript, true));

        job.save();

        LOGGER.log(Level.INFO, "Created/updated job: {0}", job.getFullName());
    }

    /** Generate the pipeline script that resolves parameters then calls shared library directly */
    private String generatePipelineScript(DeploymentJob jobConfig) {
        DeploymentGlobalConfiguration config = DeploymentGlobalConfiguration.get();
        DeploymentTemplate template = config.getTemplate(jobConfig.getTemplateName());

        if (template == null) {
            return String.format("error('Template not found: %s')", jobConfig.getTemplateName());
        }

        String templateScript = template.getScript();
        if (templateScript == null || templateScript.trim().isEmpty()) {
            return String.format("error('Template %s has no script defined')", jobConfig.getTemplateName());
        }

        // Simple parameter resolution and template execution
        return String.format(
                """
            def deployParams = resolveDeployParams(jobId: '%s')

            %s
            """,
                jobConfig.getId(), templateScript);
    }

    /** Ensure the root folder exists */
    private Folder ensureRootFolder() throws IOException {
        Jenkins jenkins = Jenkins.get();
        Folder rootFolder = (Folder) jenkins.getItem(ROOT_FOLDER_NAME);

        if (rootFolder == null) {
            rootFolder = new Folder(jenkins, ROOT_FOLDER_NAME);
            rootFolder.setDisplayName("Components");
            rootFolder.setDescription("Deployment components root folder");
            jenkins.add(rootFolder, ROOT_FOLDER_NAME);
            rootFolder.save();
        }

        return rootFolder;
    }

    /** Ensure a category folder exists */
    private Folder ensureCategoryFolder(Folder parent, String category) throws IOException {
        String sanitizedCategory = sanitizeName(category);
        Folder categoryFolder = (Folder) parent.getItem(sanitizedCategory);

        if (categoryFolder == null) {
            categoryFolder = new Folder(parent, sanitizedCategory);
            categoryFolder.setDisplayName(category);
            categoryFolder.setDescription(String.format("Jobs in %s category", category));
            parent.add(categoryFolder, sanitizedCategory);
            categoryFolder.save();
        }

        return categoryFolder;
    }

    /** Create parameters by reading from job template and applying job overrides */
    private List<ParameterDefinition> createParameters(DeploymentJob jobConfig) {
        List<ParameterDefinition> parameters = new ArrayList<>();

        // Get template from global configuration
        DeploymentGlobalConfiguration config = DeploymentGlobalConfiguration.get();
        DeploymentTemplate template = config.getTemplate(jobConfig.getTemplateName());

        if (template != null) {
            // Get all template parameters
            List<ParameterDefinition> templateParams = template.toParameterDefinitions();

            // Add parameters that are not overridden by job config
            for (ParameterDefinition templateParam : templateParams) {
                String paramName = templateParam.getName();

                // Check if job provides a fixed value for this parameter
                boolean hasFixedValue =
                        jobConfig.getParams().stream().anyMatch(param -> paramName.equals(param.getName()));

                if (!hasFixedValue) {
                    parameters.add(templateParam);
                }
            }
        } else {
            LOGGER.log(
                    Level.WARNING,
                    "Template not found: {0}. Adding basic environment parameter only.",
                    jobConfig.getTemplateName());

            // Fallback to basic environment parameter
            parameters.add(new EnvironmentChoiceParameterDefinition("environment", "Environment to deploy to", null));
        }

        return parameters;
    }

    /** Sanitize name for use as Jenkins item name */
    private String sanitizeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    /** Get deployment job by ID from global configuration */
    public static DeploymentJob getDeploymentJobById(String jobId) throws Exception {
        DeploymentGlobalConfiguration config = DeploymentGlobalConfiguration.get();

        for (DeploymentJob job : config.getJobs()) {
            if (jobId.equals(job.getId())) {
                return job;
            }
        }

        return null;
    }
}
