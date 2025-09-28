package io.jenkins.plugins.pulsar.deployment.model;

import hudson.model.ParameterDefinition;
import java.util.ArrayList;
import java.util.List;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class DeploymentTemplate {
    private final String name;
    private String description;
    private List<PromptDefinition> params = new ArrayList<>();
    private String script;

    @DataBoundConstructor
    public DeploymentTemplate(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    @DataBoundSetter
    public void setDescription(String description) {
        this.description = description;
    }

    public List<PromptDefinition> getParams() {
        return params;
    }

    @DataBoundSetter
    public void setParams(List<PromptDefinition> params) {
        this.params = params != null ? params : new ArrayList<>();
    }

    public String getScript() {
        return script;
    }

    @DataBoundSetter
    public void setScript(String script) {
        this.script = script;
    }

    /** Convert template parameters to Jenkins ParameterDefinitions */
    public List<ParameterDefinition> toParameterDefinitions() {
        List<ParameterDefinition> defs = new ArrayList<>();
        for (PromptDefinition p : params) {
            ParameterDefinition def = p.toParameterDefinition();
            if (def != null) {
                defs.add(def);
            }
        }
        return defs;
    }
}
