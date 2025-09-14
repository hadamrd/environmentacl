package io.jenkins.plugins.environmentacl.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EnvironmentACLConfig {

    public List<EnvironmentGroupConfig> environmentGroups = new ArrayList<>();
    public List<ACLRuleConfig> rules = new ArrayList<>();

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EnvironmentGroupConfig {
        public String name;
        public String description = "";
        public List<String> environments = new ArrayList<>();
        public List<String> sshKeys = new ArrayList<>();
        public List<String> vaultKeys = new ArrayList<>();
        public List<String> nodeLabels = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ACLRuleConfig {
        public String name;
        public String type;
        public int priority = 0;
        public List<String> jobs = new ArrayList<>();
        public List<String> environments = new ArrayList<>();
        public List<String> envCategories = new ArrayList<>();
        public List<String> users = new ArrayList<>();
        public List<String> groups = new ArrayList<>();
    }
}
