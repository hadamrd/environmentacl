package io.jenkins.plugins.pulsar;

import hudson.Extension;
import hudson.init.Initializer;
import hudson.init.InitMilestone;
import io.jenkins.plugins.casc.ConfigurationAsCode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Extension
public class DelayedJCascLoader {
    
    @Initializer(after = InitMilestone.JOB_CONFIG_ADAPTED)
    public static void loadConfiguration() throws Exception {
        Path configPath = Paths.get("src/test/resources/jenkins-config");
        if (Files.exists(configPath)) {
            ConfigurationAsCode.get().configure(configPath.toString());
            System.out.println("=== JCasC loaded manually after plugin initialization ===");
        }
    }
}