package io.jenkins.plugins.environmentacl.step;

import hudson.Extension;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.Serializable;
import java.util.Set;
import java.util.Collections;

public class EnvironmentACLStep extends Step implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String environment;
    private boolean failOnDeny = true;

    @DataBoundConstructor
    public EnvironmentACLStep(String environment) {
        this.environment = environment;
    }

    public String getEnvironment() {
        return environment;
    }

    public boolean isFailOnDeny() {
        return failOnDeny;
    }

    @DataBoundSetter
    public void setFailOnDeny(boolean failOnDeny) {
        this.failOnDeny = failOnDeny;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new EnvironmentACLStepExecution(this, context);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {
        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Collections.singleton(hudson.model.TaskListener.class);
        }

        @Override
        public String getFunctionName() {
            return "checkEnvironmentACL";
        }

        @Override
        public String getDisplayName() {
            return "Check Environment ACL";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return false;
        }
    }
}