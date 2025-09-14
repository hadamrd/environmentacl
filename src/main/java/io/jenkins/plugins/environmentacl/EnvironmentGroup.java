package io.jenkins.plugins.environmentacl;

import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.Extension;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.List;
import java.util.Objects;

/**
 * Represents an environment group with associated credentials and environments
 */
public class EnvironmentGroup extends AbstractDescribableImpl<EnvironmentGroup> {
    
    private final String name;
    private final String description;
    private final List<String> environments;
    private final List<String> sshKeyIds;
    private final List<String> vaultKeyIds;
    private final List<String> nodeLabels;
    
    @DataBoundConstructor
    public EnvironmentGroup(String name, String description, List<String> environments, 
                           List<String> sshKeyIds, List<String> vaultKeyIds, List<String> nodeLabels) {
        this.name = name;
        this.description = description;
        this.environments = environments;
        this.sshKeyIds = sshKeyIds;
        this.vaultKeyIds = vaultKeyIds;
        this.nodeLabels = nodeLabels;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public List<String> getEnvironments() {
        return environments;
    }
    
    public List<String> getSshKeyIds() {
        return sshKeyIds;
    }
    
    public List<String> getVaultKeyIds() {
        return vaultKeyIds;
    }
    
    public List<String> getNodeLabels() {
        return nodeLabels;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EnvironmentGroup that = (EnvironmentGroup) o;
        return Objects.equals(name, that.name);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
    
    @Extension
    public static class DescriptorImpl extends Descriptor<EnvironmentGroup> {
        @Override
        public String getDisplayName() {
            return "Environment Group";
        }
    }
}



