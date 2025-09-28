package io.jenkins.plugins.pulsar.deployment.step;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.model.Run;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Pipeline step for deploying jobs - returns resolved parameters instead of executing
 * 
 * Usage: def params = deployJob(jobId: 'my-job-id', config: [environment: 'prod'])
 */
public class DeployJobStep extends Step {
    
    private final String jobId;
    private Map<String, Object> config = Collections.emptyMap();
    
    @DataBoundConstructor
    public DeployJobStep(String jobId) {
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
    public StepExecution start(StepContext context) {
        return new DeployJobStepExecution(context, jobId, config);
    }
    
    @Extension
    public static class DescriptorImpl extends StepDescriptor {
        
        @Override
        public String getFunctionName() {
            return "deployJob";
        }
        
        @Override
        public String getDisplayName() {
            return "Deploy Job - Resolve Parameters";
        }
        
        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return new HashSet<>(Arrays.asList(
                TaskListener.class,
                Run.class  // Only need basic context, no CpsScript
            ));
        }
        
        @Override
        public boolean takesImplicitBlockArgument() {
            return false;
        }
    }
}