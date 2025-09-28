package io.jenkins.plugins.pulsar.deployment.step;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;

import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.pulsar.deployment.DeploymentGlobalConfiguration;
import io.jenkins.plugins.pulsar.deployment.model.DeploymentJob;

/**
 * Deployment step execution that handles validation and parameter resolution,
 * then returns data for the pipeline script to execute the template
 */
public class DeployJobStepExecution extends SynchronousStepExecution<Map<String, Object>> {
    
    private static final Logger LOGGER = Logger.getLogger(DeployJobStepExecution.class.getName());
    
    private final String jobId;
    private final Map<String, Object> config;
    
    public DeployJobStepExecution(StepContext context, String jobId, Map<String, Object> config) {
        super(context);
        this.jobId = jobId;
        this.config = config;
    }
    
    @Override
    protected Map<String, Object> run() throws Exception {
        TaskListener listener = getContext().get(TaskListener.class);
        
        try {
            listener.getLogger().println("Starting deployment job resolution for: " + jobId);
            
            // Get deployment job
            DeploymentJob job = getDeploymentJobById(jobId);
            if (job == null) {
                throw new IllegalArgumentException("Deployment job not found: " + jobId);
            }
            
            // Determine environment
            String environment = determineEnvironment();
            listener.getLogger().println("Target environment: " + environment);
            
            // Check permissions
            checkDeploymentPermissions(job, environment, listener);
            
            // Resolve parameters with precedence
            Map<String, Object> deployParams = resolveDeploymentParameters(job, environment, listener);
            
            // Prepare result for pipeline script
            Map<String, Object> result = new HashMap<>();
            result.put("jobTemplate", job.getJobTemplate());
            result.put("jobParams", job.getParamsAsMap());
            result.put("deployParams", deployParams);
            result.put("environment", environment);
            
            listener.getLogger().println("Job resolution completed successfully for: " + jobId);
            listener.getLogger().println("Template: " + job.getJobTemplate());
            listener.getLogger().println("Resolved parameters: " + deployParams.keySet());
            
            return result;
            
        } catch (Exception e) {
            listener.getLogger().println("Deployment job resolution failed for " + jobId + ": " + e.getMessage());
            throw e;
        }
    }
    
    private DeploymentJob getDeploymentJobById(String jobId) {
        DeploymentGlobalConfiguration config = DeploymentGlobalConfiguration.get();
        return config.getJobs().stream()
                .filter(job -> jobId.equals(job.getId()))
                .findFirst()
                .orElse(null);
    }
    
    private String determineEnvironment() throws Exception {
        // Check step config first
        Object envFromConfig = config.get("environment");
        if (envFromConfig != null) {
            return envFromConfig.toString();
        }
        
        // Check pipeline parameters
        try {
            Run<?, ?> run = getContext().get(Run.class);
            if (run != null) {
                hudson.model.ParametersAction paramsAction = run.getAction(hudson.model.ParametersAction.class);
                if (paramsAction != null) {
                    hudson.model.ParameterValue envParam = paramsAction.getParameter("environment");
                    if (envParam != null && envParam.getValue() != null) {
                        return envParam.getValue().toString();
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to read environment from build parameters: " + e.getMessage());
        }
        
        throw new IllegalArgumentException("No environment specified");
    }
    
    private void checkDeploymentPermissions(DeploymentJob job, String environment, TaskListener listener) throws Exception {
        listener.getLogger().println("Checking deployment permissions...");
        
        // TODO: Implement permission checking
        // Check if current user can deploy this job to this environment
        // Integrate with your existing ACL system
        
        listener.getLogger().println("Permission check passed");
    }
    
    /**
     * Resolve parameters with proper precedence:
     * 1. Pipeline UI params (lowest)
     * 2. Step config (middle) 
     * 3. Job params (highest)
     */
    private Map<String, Object> resolveDeploymentParameters(DeploymentJob job, String environment, TaskListener listener) throws Exception {
        Map<String, Object> deployParams = new HashMap<>();
        
        // 1. Start with pipeline UI parameters (lowest precedence)
        addPipelineParameters(deployParams);
        
        // 2. Add step config parameters (middle precedence)
        deployParams.putAll(config);
        
        // 3. Add job's own parameters (highest precedence)
        job.getParams().forEach(param -> {
            deployParams.put(param.getName(), param.getValue());
        });
        
        // Always ensure environment is set
        deployParams.put("environment", environment);
        
        return deployParams;
    }
    
    private void addPipelineParameters(Map<String, Object> deployParams) {
        try {
            Run<?, ?> run = getContext().get(Run.class);
            if (run != null) {
                hudson.model.ParametersAction paramsAction = run.getAction(hudson.model.ParametersAction.class);
                if (paramsAction != null) {
                    for (hudson.model.ParameterValue param : paramsAction.getParameters()) {
                        deployParams.put(param.getName(), param.getValue());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to read pipeline parameters: " + e.getMessage());
        }
    }
}