package io.jenkins.plugins.pulsar.shared;

import org.jenkinsci.plugins.workflow.steps.StepContext;

public class PipelineHelper {

    public static String getParameter(StepContext context, String paramName) {
        try {
            hudson.model.Run<?, ?> run = context.get(hudson.model.Run.class);
            if (run != null) {
                hudson.model.ParametersAction params = run.getAction(hudson.model.ParametersAction.class);
                if (params != null) {
                    hudson.model.ParameterValue param = params.getParameter(paramName);
                    if (param != null && param.getValue() != null) {
                        return param.getValue().toString();
                    }
                }
            }
        } catch (Exception e) {
            // Swallow exception, return null
        }
        return null;
    }

    public static String getRequiredParameter(StepContext context, String paramName) throws Exception {
        String value = getParameter(context, paramName);
        if (value == null) {
            throw new IllegalArgumentException("Required parameter '" + paramName + "' not found");
        }
        return value;
    }
}
