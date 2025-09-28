package io.jenkins.plugins.pulsar.deployment.step;

import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.pulsar.deployment.DeploymentGlobalConfiguration;
import io.jenkins.plugins.pulsar.deployment.model.DeploymentJob;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;

public class ResolveDeployParamsStepExecution extends SynchronousStepExecution<Map<String, Object>> {

    private static final Logger LOGGER = Logger.getLogger(ResolveDeployParamsStepExecution.class.getName());

    private final String jobId;
    private final Map<String, Object> config;

    public ResolveDeployParamsStepExecution(StepContext context, String jobId, Map<String, Object> config) {
        super(context);
        this.jobId = jobId;
        this.config = config;
    }

    @Override
    protected Map<String, Object> run() throws Exception {
        TaskListener listener = getContext().get(TaskListener.class);

        listener.getLogger().println("Resolving deployment parameters for job: " + jobId);

        // Get deployment job configuration
        DeploymentJob job = getDeploymentJobById(jobId);
        if (job == null) {
            throw new IllegalArgumentException("Deployment job not found: " + jobId);
        }

        Map<String, Object> deployParams = new HashMap<>();

        // 1. Start with pipeline UI parameters (lowest precedence)
        addPipelineParameters(deployParams);
        listener.getLogger().println("UI parameters: " + deployParams.keySet());

        // 2. Add step config parameters (middle precedence)
        deployParams.putAll(config);
        listener.getLogger().println("After step config: " + deployParams.keySet());

        // 3. Add job's fixed parameters (highest precedence)
        job.getParams().forEach(param -> {
            deployParams.put(param.getName(), param.getValue());
            listener.getLogger().println("Job override: " + param.getName() + " = " + param.getValue());
        });

        // Determine environment with precedence
        String environment = determineEnvironment(deployParams);
        deployParams.put("environment", environment);

        listener.getLogger().println("Final resolved parameters: " + deployParams);
        return deployParams;
    }

    private String determineEnvironment(Map<String, Object> currentParams) throws Exception {
        // Check step config first
        Object envFromConfig = config.get("environment");
        if (envFromConfig != null) {
            return envFromConfig.toString();
        }

        // Check current resolved params
        Object envFromParams = currentParams.get("environment");
        if (envFromParams != null) {
            return envFromParams.toString();
        }

        throw new IllegalArgumentException("No environment specified in parameters");
    }

    private DeploymentJob getDeploymentJobById(String jobId) {
        DeploymentGlobalConfiguration config = DeploymentGlobalConfiguration.get();
        return config.getJobs().stream()
                .filter(job -> jobId.equals(job.getId()))
                .findFirst()
                .orElse(null);
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
            LOGGER.log(Level.WARNING, "Failed to read pipeline parameters: {0}", e.getMessage());
        }
    }
}
