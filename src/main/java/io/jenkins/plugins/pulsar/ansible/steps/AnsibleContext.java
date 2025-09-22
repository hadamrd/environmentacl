package io.jenkins.plugins.pulsar.ansible.steps;

import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.pulsar.ansible.AnsibleProjectsGlobalConfiguration;
import io.jenkins.plugins.pulsar.ansible.model.AnsibleProject;
import io.jenkins.plugins.pulsar.ansible.model.AnsibleVault;
import io.jenkins.plugins.pulsar.ansible.service.AnsibleEnvironmentService;
import io.jenkins.plugins.pulsar.ansible.service.AnsiblePlaybookCommandBuilder;
import io.jenkins.plugins.pulsar.ansible.service.VaultManager;
import io.jenkins.plugins.pulsar.container.service.ContainerManager;
import io.jenkins.plugins.pulsar.container.steps.SharedContainerStep;
import io.jenkins.plugins.pulsar.shared.LaunchHelper;
import io.jenkins.plugins.pulsar.ssh.service.SshAgent;
import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.jenkinsci.plugins.workflow.steps.StepContext;

/**
 * Manages Ansible project execution environment with SSH agent, vault files, and project setup.
 * Uses singleton pattern with reference counting similar to ContainerManager.
 */
public class AnsibleContext implements Serializable {
    private static final long serialVersionUID = 1L;

    // Static registry of active contexts per node
    private static final Map<String, AnsibleContext> activeContexts = new ConcurrentHashMap<>();

    private final String projectId;
    private final String ref;
    private final AnsibleProject project;
    private final String projectDir;
    private final String nodeName;
    private final String contextKey;

    // Managed resources
    private ContainerManager execEnv;
    private SshAgent sshAgent;
    private VaultManager vaultManager;

    // Lifecycle management
    private int referenceCount = 1;
    private volatile boolean isKilled = false;
    private boolean initialized = false;

    // Services
    private final transient AnsibleEnvironmentService envService;

    private AnsibleContext(
            String projectId,
            String ref,
            AnsibleProject project,
            String nodeName,
            String contextKey,
            String execEnvProjectRoot) {
        this.projectId = projectId;
        this.ref = ref;
        this.project = project;
        this.nodeName = nodeName;
        this.envService = new AnsibleEnvironmentService();
        this.projectDir = execEnvProjectRoot;
        this.contextKey = contextKey;
    }

    /** Get or create cached AnsibleContext - similar to ContainerManager.getOrCreate */
    public static AnsibleContext getOrCreate(
            String projectId, String ref, StepContext stepContext, List<String> containerOptions) throws Exception {

        TaskListener listener = stepContext.get(TaskListener.class);
        Launcher launcher = stepContext.get(Launcher.class);

        // Find the project
        AnsibleProjectsGlobalConfiguration config = AnsibleProjectsGlobalConfiguration.get();
        AnsibleProject project = config.getProjectById(projectId);

        if (project == null) {
            throw new IllegalArgumentException("Ansible project not found: " + projectId);
        }

        if (ref == null) {
            throw new IllegalArgumentException("Version information required");
        }

        // Get node name
        String nodeName = LaunchHelper.getNodeName(stepContext);
        String contextKey = String.format("%s:%s:%s", nodeName, projectId, ref);

        synchronized (AnsibleContext.class) {
            AnsibleContext existing = activeContexts.get(contextKey);
            if (existing != null && existing.isValid(launcher, listener)) {
                existing.referenceCount++;
                listener.getLogger().println("Reusing existing Ansible context: " + projectId);
                return existing;
            }

            // Create new context
            String sRef = ref.replaceAll("[^a-zA-Z0-9\\-\\.]", "_");
            String projectDir = String.format("/%s/%s", projectId, sRef);
            AnsibleContext context = new AnsibleContext(
                    projectId, ref, project, nodeName, contextKey, projectDir);

            // Initialize everything
            context.initialize(stepContext, containerOptions, launcher, listener);

            activeContexts.put(contextKey, context);

            return context;
        }
    }

    /** Initialize the full Ansible environment */
    private void initialize(
            StepContext stepContext, List<String> containerOptions, Launcher launcher, TaskListener listener)
            throws Exception {

        if (initialized) return;

        listener.getLogger().println("Setting up Ansible environment...");

        // 1. Start SSH agent
        this.sshAgent = SshAgent.getInstance(nodeName);
        this.sshAgent.start(launcher, listener);

        // 2. Create container with SSH socket mount
        this.execEnv = spinExecEnv(stepContext, containerOptions, launcher, listener);

        // 3. Initialize vault manager
        this.vaultManager = new VaultManager(execEnv);

        // 4. Setup project
        setup(stepContext, launcher, listener);

        initialized = true;
        listener.getLogger().println("Ansible environment ready");
    }

    /** Create or get shared container */
    private ContainerManager spinExecEnv(StepContext stepContext, List<String> containerOptions, 
                                    Launcher launcher, TaskListener listener) throws Exception {
        List<String> finalOpts = new ArrayList<>();
        if (containerOptions != null) {
            finalOpts.addAll(containerOptions);
        }

        // Mount the entire SSH agents directory instead of individual sockets
        String sshAgentsDir = SshAgent.getSshAgentsDir();
        finalOpts.add(String.format("-v %s:%s", sshAgentsDir, sshAgentsDir));

        SharedContainerStep containerStep = createContainerStep(finalOpts);
        var container = ContainerManager.getOrCreate(nodeName, project.getExecEnv(), containerStep, launcher, listener);
        
        // Set SSH_AUTH_SOCK after agent is started
        if (sshAgent != null && sshAgent.getSocketPath() != null) {
            container.setEnv("SSH_AUTH_SOCK", sshAgent.getSocketPath());
        }

        return container;
    }

    /** Ensure SSH agent is running, restart if needed */
    private void ensureSshAgentRunning(Launcher launcher, TaskListener listener) throws Exception {
        if (sshAgent == null) {
            listener.getLogger().println("No SSH agent instance, creating new one...");
            this.sshAgent = SshAgent.getInstance(nodeName);
        }
        
        if (!sshAgent.isRunning(launcher, listener)) {
            listener.getLogger().println("SSH agent not running, restarting...");

            // Clean up stale socket BEFORE restarting
            String oldSocketPath = sshAgent.getSocketPath();
            if (oldSocketPath != null) {
                List<String> cleanupCmd = Arrays.asList("rm", "-f", oldSocketPath);
                LaunchHelper.executeQuietlyDiscardOutput(launcher, cleanupCmd, 5, listener);
                listener.getLogger().println("Cleaned up stale socket: " + oldSocketPath);
            }

            sshAgent.start(launcher, listener);

            execEnv.setEnv("SSH_AUTH_SOCK", sshAgent.getSocketPath());
            listener.getLogger().println("Updated container SSH_AUTH_SOCK to: " + sshAgent.getSocketPath());
        } else {
            if (!sshAgent.isSynced(launcher, listener)) {
                sshAgent.clearLoadedKeys();
            }
        }
    }

    /** Setup SSH agent with all required keys */
    public void setupEnvSshKeys(Run<?, ?> run, Launcher launcher, TaskListener listener, String envName)
            throws Exception {

        String envSshCredentialId = envService.getEnvSshCred(projectId, envName);
        if (envSshCredentialId == null) {
            listener.getLogger().println("No SSH keys configured for environment: " + envName);
            return;
        }

        // ENSURE SSH agent is actually running before loading keys
        ensureSshAgentRunning(launcher, listener);

        if (!sshAgent.hasKey(envSshCredentialId)) {
            sshAgent.loadKeys(Arrays.asList(envSshCredentialId), run, launcher, listener);
        }
    }

    /** Setup vault password files - now delegates to VaultManager */
    public void setupVaultFiles(Run<?, ?> run, Launcher launcher, TaskListener listener, String envName)
            throws Exception {
        List<AnsibleVault> envVaults = project.getEnvVaults(envName);

        vaultManager.setupVaultFiles(envVaults, run, launcher, listener);
    }

    /** Setup project files (checkout, ansible.cfg) */
    private void setup(StepContext stepContext, Launcher launcher, TaskListener listener) throws Exception {
        listener.getLogger().println("Setting up project files...");

        // Checkout project
        checkoutProject(launcher, listener);

        // Generate and write ansible.cfg
        setupAnsibleConfig(launcher, listener);
    }

    /** Checkout project from repository */
    private void checkoutProject(Launcher launcher, TaskListener listener) throws Exception {
        // One command: try to reuse existing repo, otherwise clone fresh
        String cmd = String.format(
            "(cd %s && git remote get-url origin 2>/dev/null | grep -q '%s' && git fetch && git reset --hard %s) || " +
            "(rm -rf %s && git clone %s %s && cd %s && git checkout %s)",
            projectDir, project.getRepository(), ref,
            projectDir, project.getRepository(), projectDir, projectDir, ref
        );
        
        if (execEnv.execute(cmd, launcher, listener) != 0) {
            throw new Exception("Failed to checkout: " + project.getRepository() + "@" + ref);
        }
        
        listener.getLogger().println("Project checked out: " + project.getRepository() + "@" + ref);
    }

    /** Setup ansible.cfg file */
    private void setupAnsibleConfig(Launcher launcher, TaskListener listener) throws Exception {
        String secureWriteCmd = String.format("cat > %s/ansible.cfg", projectDir);

        ByteArrayInputStream configInput =
                new ByteArrayInputStream(project.getAnsibleConfig().getBytes());

        int result = execEnv.execute(secureWriteCmd, null, null, configInput, launcher, listener);

        if (result != 0) {
            throw new Exception("Failed to write ansible.cfg file");
        }

        listener.getLogger().println("ansible.cfg configured");
    }

    /** Execute ansible-playbook with all credentials available */
    public int runPlaybook(
            String playbook,
            String envName,
            Map<String, Object> extraVars,
            String options,
            String user,
            Launcher launcher,
            TaskListener listener)
            throws Exception {

        ensureInitialized();

        if (isKilled) {
            throw new IllegalStateException("AnsibleContext has been killed");
        }

        // Use command builder for clean separation of concerns
        AnsiblePlaybookCommandBuilder builder = new AnsiblePlaybookCommandBuilder()
                .playbook(playbook)
                .user(user != null ? user : "ansible")
                .vaultManager(vaultManager)
                .inventory(getInventoryPath(envName))
                .projectRoot(projectDir)
                .extraVars(extraVars)
                .options(options);

        // Log command summary
        listener.getLogger().println("=== Executing Ansible Playbook ===");
        listener.getLogger().println(builder.getSummary());

        // Build and execute command
        String fullCmd = builder.buildCmd();
        return execEnv.execute(fullCmd, "root", launcher, listener);
    }

    /** Execute a command in the project environment */
    public int executeCommand(String command, Launcher launcher, TaskListener listener) throws Exception {
        ensureInitialized();

        if (isKilled) {
            throw new IllegalStateException("AnsibleContext has been killed");
        }

        String fullCmd = "cd " + projectDir + " && " + command;
        if (sshAgent != null && sshAgent.getSocketPath() != null) {
            fullCmd = "export SSH_AUTH_SOCK=" + sshAgent.getSocketPath() + " && " + fullCmd;
        }

        return execEnv.execute(fullCmd, "root", launcher, listener);
    }

    /** Release reference - similar to ContainerManager.release */
    public void release(boolean cleanup, Launcher launcher, TaskListener listener) {
        synchronized (AnsibleContext.class) {
            referenceCount--;

            if (referenceCount <= 0 && cleanup) {
                listener.getLogger().println("Cleaning up Ansible project: " + projectId);
                deleteContext(launcher, listener);
                activeContexts.remove(contextKey);
            } else if (referenceCount <= 0) {
                listener.getLogger().println("Keeping Ansible context alive: " + projectId);
            }
        }
    }

    /** Full cleanup */
    private void deleteContext(Launcher launcher, TaskListener listener) {
        if (isKilled) return;

        try {
            // 2. Release SSH keys
            if (sshAgent != null) {
                sshAgent.stop(launcher, listener);
            }

            // 3. Release container (this will call ContainerManager.release)
            if (execEnv != null) {
                execEnv.release(true, launcher, listener); // cleanup=true
            } else {
                listener.getLogger().println("Exec environment not found when cleaning up context!");
            }

            listener.getLogger().println("Ansible context cleaned up: " + projectId);

        } catch (Exception e) {
            listener.getLogger()
                    .println("Warning: Failed to cleanup Ansible context " + projectId + ": " + e.getMessage());
        } finally {
            isKilled = true;
        }
    }

    /** Check if context is still valid */
    private boolean isValid(Launcher launcher, TaskListener listener) {
        return !isKilled && execEnv != null && execEnv.isRunning(launcher, listener);
    }

    /** Ensure context is initialized */
    private void ensureInitialized() throws Exception {
        if (!initialized) {
            throw new IllegalStateException("AnsibleContext not properly initialized");
        }
    }

    /** Get inventory path for environment */
    public String getInventoryPath(String envName) throws Exception {
        return envService.getInventoryPath(projectId, envName);
    }

    /** Create SharedContainerStep for existing container system */
    private SharedContainerStep createContainerStep(List<String> containerOptions) {
        SharedContainerStep step = new SharedContainerStep(project.getExecEnv());

        if (containerOptions != null && !containerOptions.isEmpty()) {
            step.setOptions(String.join(" ", containerOptions));
        }

        return step;
    }

    /** Cleanup all contexts */
    public static void cleanupAll(Launcher launcher, TaskListener listener) {
        synchronized (AnsibleContext.class) {
            List<AnsibleContext> contexts = new ArrayList<>(activeContexts.values());

            for (AnsibleContext context : contexts) {
                try {
                    context.deleteContext(launcher, listener);
                } catch (Exception e) {
                    listener.getLogger()
                            .println("Warning: Failed to cleanup context " + context.projectId + ": " + e.getMessage());
                }
            }
            activeContexts.clear();
        }
    }

    // Getters
    public String getProjectId() {
        return projectId;
    }

    public String getRef() {
        return ref;
    }

    public AnsibleProject getProject() {
        return project;
    }

    public String getProjectDir() {
        return projectDir;
    }

    public String getNodeName() {
        return nodeName;
    }

    public ContainerManager getExecEnv() {
        return execEnv;
    }

    public SshAgent getSshAgent() {
        return sshAgent;
    }

    public int getReferenceCount() {
        return referenceCount;
    }

    public boolean isKilled() {
        return isKilled;
    }

    public Set<String> getSetupVaultNames() {
        return vaultManager != null ? vaultManager.getSetupVaultNames() : Collections.emptySet();
    }

    public VaultManager getVaultManager() {
        return vaultManager;
    }
}
