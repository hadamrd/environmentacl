package io.jenkins.plugins.environmentacl;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import io.jenkins.plugins.environmentacl.model.EnvironmentACLConfig.EnvironmentGroupConfig;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import org.kohsuke.stapler.DataBoundConstructor;

/** Build wrapper that provides environment information to the build */
public class EnvironmentAccessBuildWrapper extends BuildWrapper {

    private final String environmentParameter;

    @DataBoundConstructor
    public EnvironmentAccessBuildWrapper(String environmentParameter) {
        this.environmentParameter = environmentParameter;
    }

    public String getEnvironmentParameter() {
        return environmentParameter;
    }

    @Override
    public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener)
            throws IOException, InterruptedException {
        ParametersAction parametersAction = build.getAction(ParametersAction.class);
        if (parametersAction != null) {
            ParameterValue param = parametersAction.getParameter(environmentParameter);
            if (param instanceof StringParameterValue) {
                String environment = ((StringParameterValue) param).getValue();

                EnvironmentACLGlobalConfiguration config = EnvironmentACLGlobalConfiguration.get();
                List<String> sshKeys = config.getSshKeysForEnvironment(environment);
                List<String> vaultKeys = config.getVaultKeysForEnvironment(environment);

                return new Environment() {
                    @Override
                    public void buildEnvVars(Map<String, String> env) {
                        env.put("SELECTED_ENVIRONMENT", environment);
                        env.put("ENVIRONMENT_SSH_KEYS", String.join(",", sshKeys));
                        env.put("ENVIRONMENT_VAULT_KEYS", String.join(",", vaultKeys));

                        EnvironmentGroupConfig group = config.getEnvironmentGroupForEnvironment(environment);
                        if (group != null) {
                            env.put("ENVIRONMENT_GROUP", group.name);
                            env.put("ENVIRONMENT_NODE_LABELS", String.join(",", group.nodeLabels));
                        }
                    }
                };
            }
        }

        return new Environment() {};
    }

    @Extension
    public static class DescriptorImpl extends BuildWrapperDescriptor {

        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return true;
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Environment Access Control";
        }
    }
}
