package io.jenkins.plugins.pulsar.deployment.step;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Simple step that resolves deployment parameters and returns them
 *
 * <p>Usage: def deployParams = resolveDeployParams(jobId: 'ping-vm')
 */
public class ResolveDeployParamsStep extends Step {

    private final String jobId;
    private Map<String, Object> config = Collections.emptyMap();

    @DataBoundConstructor
    public ResolveDeployParamsStep(String jobId) {
        this.jobId = jobId;
    }

    public String getJobId() {
        return jobId;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    @DataBoundSetter
    public void setConfig(Map<String, Object> config) {
        this.config = config != null ? config : Collections.emptyMap();
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new ResolveDeployParamsStepExecution(context, jobId, config);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "resolveDeployParams";
        }

        @Override
        public String getDisplayName() {
            return "Resolve Deployment Parameters";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return new HashSet<>(Arrays.asList(TaskListener.class, Run.class));
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return false;
        }
    }
}
