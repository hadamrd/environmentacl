package io.jenkins.plugins.environmentacl;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.verb.POST;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import hudson.Extension;
import hudson.model.ManagementLink;
import hudson.security.Permission;
import hudson.util.FormValidation;
import io.jenkins.plugins.environmentacl.model.EnvironmentACLConfig;
import jenkins.model.Jenkins;

/**
 * The ONLY UI for Environment ACL configuration.
 * GlobalConfiguration exists only for JCasC support.
 */
@Extension
public class EnvironmentACLManagementLink extends ManagementLink {

    private static final Logger LOGGER = Logger.getLogger(EnvironmentACLManagementLink.class.getName());
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    @Override
    public String getIconFileName() {
        return "symbol-folder-outline";
    }

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
        return Category.SECURITY;
    }

    /**
     * Get the configuration instance - used by Jelly templates
     */
    public EnvironmentACLGlobalConfiguration getConfiguration() {
        return EnvironmentACLGlobalConfiguration.get();
    }
    
    /**
     * Get current configuration as YAML string for display in UI
     */
    public String getCurrentYamlConfiguration() {
        try {
            EnvironmentACLGlobalConfiguration config = getConfiguration();
            
            // If no configuration exists, return example
            if (config.getEnvironmentGroups().isEmpty() && config.getRules().isEmpty()) {
                return getExampleConfiguration();
            }
            
            EnvironmentACLConfig aclConfig = new EnvironmentACLConfig();
            aclConfig.environmentGroups = config.getEnvironmentGroups();
            aclConfig.rules = config.getRules();
            
            return YAML_MAPPER.writeValueAsString(aclConfig);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to generate YAML", e);
            return getExampleConfiguration();
        }
    }
    
    private String getExampleConfiguration() {
        return "# Example configuration\n" +
               "environmentGroups:\n" +
               "  - name: production\n" +
               "    description: Production environments\n" +
               "    environments:\n" +
               "      - prod-us-east\n" +
               "    sshKeys:\n" +
               "      - prod-ssh-key\n" +
               "\n" +
               "rules:\n" +
               "  - name: Admin Access\n" +
               "    type: allow\n" +
               "    priority: 100\n" +
               "    jobs:\n" +
               "      - \"*\"\n" +
               "    environments:\n" +
               "      - \"*\"\n" +
               "    groups:\n" +
               "      - administrators\n";
    }

    /**
     * Handle YAML configuration submission from UI
     */
    @POST
    public void doConfigSubmit(StaplerRequest req, StaplerResponse rsp) throws Exception {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        String yamlConfiguration = req.getParameter("yamlConfiguration");

        if (yamlConfiguration != null && !yamlConfiguration.trim().isEmpty()) {
            try {
                // Parse YAML
                EnvironmentACLConfig aclConfig = YAML_MAPPER.readValue(
                    yamlConfiguration, 
                    EnvironmentACLConfig.class
                );
                
                // Save to GlobalConfiguration (which handles persistence)
                EnvironmentACLGlobalConfiguration config = EnvironmentACLGlobalConfiguration.get();
                config.setEnvironmentGroups(aclConfig.environmentGroups);
                config.setRules(aclConfig.rules);
                
                LOGGER.info("Configuration saved successfully");
                rsp.sendRedirect(".");
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to parse YAML", e);
                rsp.sendRedirect(".?error=Invalid+YAML:+" + e.getMessage());
            }
        } else {
            rsp.sendRedirect(".?error=Empty+configuration");
        }
    }
    
    /**
     * Validate YAML configuration
     */
    @POST
    public FormValidation doCheckYamlConfiguration(@QueryParameter String value) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        
        if (value == null || value.trim().isEmpty()) {
            return FormValidation.warning("Configuration is empty");
        }
        
        try {
            YAML_MAPPER.readValue(value, EnvironmentACLConfig.class);
            return FormValidation.ok("Valid configuration");
        } catch (Exception e) {
            return FormValidation.error("Invalid YAML: " + e.getMessage());
        }
    }
    
    /**
     * Export configuration in JCasC format
     */
    @POST
    public void doExportJCasC(StaplerRequest req, StaplerResponse rsp) throws Exception {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        
        try {
            String yamlContent = getCurrentYamlConfiguration();
            
            // Indent for JCasC format
            String indented = yamlContent.replaceAll("(?m)^", "    ");
            String jcascFormat = "# Jenkins Configuration as Code\n" +
                                "# Add this to your jenkins.yaml\n\n" +
                                "unclassified:\n" +
                                "  environmentACL:\n" + indented;
            
            rsp.setContentType("text/yaml");
            rsp.setHeader("Content-Disposition", "attachment; filename=\"jenkins-environment-acl.yaml\"");
            rsp.getWriter().write(jcascFormat);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to export", e);
            rsp.sendError(500, "Export failed: " + e.getMessage());
        }
    }
}