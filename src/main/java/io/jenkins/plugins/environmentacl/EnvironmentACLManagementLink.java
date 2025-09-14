package io.jenkins.plugins.environmentacl;

import javax.annotation.Nonnull;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.verb.POST;

import hudson.Extension;
import hudson.model.ManagementLink;
import hudson.security.Permission;
import jenkins.model.Jenkins;

/**
 * Management link for Environment ACL configuration
 * This adds an entry to the "Manage Jenkins" page
 */
@Extension
public class EnvironmentACLManagementLink extends ManagementLink {
    
    @Override
    public String getIconFileName() {
        return "symbol-folder-outline";
    }
    
    @Nonnull
    @Override
    public String getDisplayName() {
        return "Environment ACL Manager";
    }
    
    @Override
    public String getUrlName() {
        return "environment-acl";
    }
    
    @Override
    public String getDescription() {
        return "Manage environment groups and access control rules for Jenkins pipelines";
    }
    
    @Override
    public Permission getRequiredPermission() {
        return Jenkins.ADMINISTER;
    }
    
    @Override
    public Category getCategory() {
        return Category.SECURITY;  // Groups it with other security-related items
    }
    
    /**
     * Get the configuration instance for the view
     * This method is called from the Jelly template
     */
    public EnvironmentACLGlobalConfiguration getConfiguration() {
        return EnvironmentACLGlobalConfiguration.get();
    }
    
    /**
     * Handle form submission from the configuration page
     */
    @POST
    public void doConfigSubmit(StaplerRequest req, StaplerResponse rsp) throws Exception {
        // Check permissions
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        
        EnvironmentACLGlobalConfiguration config = EnvironmentACLGlobalConfiguration.get();
        
        // Get the YAML configuration from the form
        String yamlConfiguration = req.getParameter("yamlConfiguration");
        
        if (yamlConfiguration != null) {
            try {
                if (!yamlConfiguration.trim().isEmpty()) {
                    // Parse and validate the YAML
                    config.parseYamlConfiguration(yamlConfiguration);
                }
                config.setYamlConfiguration(yamlConfiguration);
                config.save();
                
                // Redirect back to the configuration page
                rsp.sendRedirect(".");
                
            } catch (Exception e) {
                // In a real implementation, you might want to show the error on the form
                throw new Exception("Failed to save configuration: " + e.getMessage());
            }
        } else {
            // If no YAML provided, just redirect back
            rsp.sendRedirect(".");
        }
    }
}
