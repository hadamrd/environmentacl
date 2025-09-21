package io.jenkins.plugins.pulsar.ansible.steps;

import hudson.AbortException;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.Secret;
import io.jenkins.plugins.pulsar.ansible.AnsibleProjectsGlobalConfiguration;
import io.jenkins.plugins.pulsar.ansible.model.AnsibleProject;
import io.jenkins.plugins.pulsar.ansible.model.AnsibleVault;
import io.jenkins.plugins.pulsar.ansible.service.AnsibleEnvironmentService;
import io.jenkins.plugins.pulsar.ansible.service.AnsiblePlaybookCommandBuilder;
import io.jenkins.plugins.pulsar.shared.LaunchHelper;
import io.jenkins.plugins.pulsar.sharedcontainer.service.ContainerManager;
import io.jenkins.plugins.pulsar.sharedcontainer.steps.SharedContainerStep;
import io.jenkins.plugins.pulsar.sshenv.service.SshAgent;

import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsProvider;

/**
 * Manages Ansible project execution environment with SSH agent, vault files, and project setup.
 * Uses singleton pattern with reference counting similar to ContainerManager.
 */
public class AnsibleContext implements Serializable {
    private static final long serialVersionUID = 1L;

    // Static registry of active contexts per node
    private static final Map<String, AnsibleContext> activeContexts = new ConcurrentHashMap<>();

    private final String projectId;
    private final Map<String, String> version;
    private final AnsibleProject project;
    private final String projectRoot;
    private final String nodeName;
    private final String contextKey;

    // Managed resources
    private ContainerManager container;
    private SshAgent sshAgent;
    private String vaultDir;
    private List<String> loadedSshCredentialIds;
    private Map<String, String> setupVaultCredentials;

    // Lifecycle management
    private int referenceCount = 1;
    private volatile boolean isKilled = false;
    private boolean initialized = false;

    // Services
    private final transient AnsibleEnvironmentService envService;

    private AnsibleContext(String projectId, Map<String, String> version, AnsibleProject project, String nodeName) {
        this.projectId = projectId;
        this.version = version;
        this.project = project;
        this.nodeName = nodeName;
        this.envService = new AnsibleEnvironmentService();
        this.loadedSshCredentialIds = new ArrayList<>();
        this.setupVaultCredentials = new HashMap<>();

        // Calculate project root
        String sanitizedVersion = version.get("ref").replaceAll("[^a-zA-Z0-9\\-\\.]", "_");
        String sanitizedType = version.get("type").replaceAll("[^a-zA-Z0-9\\-]", "_");
        this.projectRoot = "/ansible/projects/" + projectId + "/" + sanitizedType + "/" + sanitizedVersion;

        // Create cache key
        this.contextKey = nodeName + ":" + projectId + ":" + version.get("ref") + ":" + version.get("type");
    }

    /** Get or create cached AnsibleContext - similar to ContainerManager.getOrCreate */
    public static AnsibleContext getOrCreate(
            String projectId, Map<String, String> version, StepContext stepContext, List<String> containerOptions)
            throws Exception {

        TaskListener listener = stepContext.get(TaskListener.class);
        Launcher launcher = stepContext.get(Launcher.class);

        // Find the project
        AnsibleProjectsGlobalConfiguration config = AnsibleProjectsGlobalConfiguration.get();
        AnsibleProject project = config.getProjectById(projectId);

        if (project == null) {
            throw new IllegalArgumentException("Ansible project not found: " + projectId);
        }

        if (version == null || version.get("ref") == null || version.get("type") == null) {
            throw new IllegalArgumentException("Version information required (ref and type)");
        }

        // Get node name
        String nodeName = LaunchHelper.getNodeName(stepContext);
        String contextKey = nodeName + ":" + projectId + ":" + version.get("ref") + ":" + version.get("type");

        synchronized (AnsibleContext.class) {
            AnsibleContext existing = activeContexts.get(contextKey);
            if (existing != null && !existing.isKilled && existing.isValid(launcher, listener)) {
                existing.referenceCount++;
                listener.getLogger().println("Reusing existing Ansible context: " + projectId);
                return existing;
            }

            // Create new context
            listener.getLogger().println("Creating new Ansible context for: " + projectId);
            AnsibleContext context = new AnsibleContext(projectId, version, project, nodeName);

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
        sshAgent.start(launcher, listener);

        // 2. Prepare container options with SSH socket mount
        List<String> finalContainerOptions = new ArrayList<>();
        if (containerOptions != null) {
            finalContainerOptions.addAll(containerOptions);
        }

        // Add SSH socket mount - mount the socket file directly like Docker socket
        String socketPath = sshAgent.getSocketPath();
        finalContainerOptions.add("-v " + socketPath + ":" + socketPath);

        // 3. Create container with socket mounted
        this.container = createOrGetContainer(finalContainerOptions, launcher, listener);

        // 4. Set SSH_AUTH_SOCK environment
        container.setEnv("SSH_AUTH_SOCK", socketPath);

        // 5. Setup project
        setupProject(launcher, listener);

        initialized = true;
        listener.getLogger().println("Ansible environment ready");
    }

    /** Create or get shared container */
    private ContainerManager createOrGetContainer(
            List<String> containerOptions, Launcher launcher, TaskListener listener) throws Exception {

        SharedContainerStep containerStep = createContainerStep(containerOptions);

        return ContainerManager.getOrCreate(nodeName, project.getExecEnv(), containerStep, launcher, listener);
    }

    /** Setup SSH agent with all required keys */
    public void setupEnvSshKeys(Run<?, ?> run, Launcher launcher, TaskListener listener, String envName) throws Exception {
        listener.getLogger().println("Setting up SSH agent...");
        String envSshCredentialId = envService.getSshCredentialForEnvironment(projectId, envName);
        if (envSshCredentialId == null) {
            listener.getLogger().println("No SSH keys configured for environment: " + envName);
            return;
        }

        if (loadedSshCredentialIds == null) {
            loadedSshCredentialIds = new ArrayList<>();
        }

        if (!loadedSshCredentialIds.contains(envSshCredentialId)) {
            sshAgent.loadKeys(Arrays.asList(envSshCredentialId), run, launcher, listener);
            loadedSshCredentialIds.add(envSshCredentialId);
        }
    }

    /** Setup vault password files */
    public void setupVaultFiles(Run<?, ?> run, Launcher launcher, TaskListener listener, String envName) throws Exception {
        listener.getLogger().println("Setting up vault files...");

        this.vaultDir = "/ansible/vaults";
        container.execute("mkdir -p " + vaultDir, launcher, listener);

        // Initialize setupVaultCredentials if it's null
        if (this.setupVaultCredentials == null) {
            this.setupVaultCredentials = new HashMap<>();
        }

        List<AnsibleVault> envVaults = project.getEnvVaults(envName);

        if (envVaults.isEmpty()) {
            listener.getLogger().println("No vaults configured for environment: " + envName);
            return;
        }

        // Setup vault files for all vaults associated with this environment's group
        for (AnsibleVault vault : envVaults) {
            String vaultCredentialId = vault.getCredentialId();
            
            if (vaultCredentialId != null && !vaultCredentialId.trim().isEmpty()) {
                if (!setupVaultCredentials.containsKey(vault.getId())) {
                    setupVaultFile(vault.getId(), vaultCredentialId, run, launcher, listener);
                    setupVaultCredentials.put(vault.getId(), vaultCredentialId);
                }
            }
        }
    }

    /** Setup a single vault file securely using stdin */
    private void setupVaultFile(
            String vaultName, String credentialId, Run<?, ?> run, Launcher launcher, TaskListener listener)
            throws Exception {

        // Get credential
        String vaultPassword = getSecretCredential(credentialId, run);
        if (vaultPassword == null) {
            listener.getLogger().println("Warning: Vault credential not found: " + credentialId);
            return;
        }

        String vaultFilePath = vaultDir + "/" + vaultName + ".txt";

        // Command that reads from stdin and writes to file with secure permissions
        String secureWriteCmd = String.format("umask 077 && cat > %s && chmod 600 %s", vaultFilePath, vaultFilePath);

        // Create input stream with vault password
        ByteArrayInputStream passwordInput = new ByteArrayInputStream(vaultPassword.getBytes());

        // Execute securely with stdin - password not visible in logs or process list
        int result = container.execute(secureWriteCmd, null, null, passwordInput, launcher, listener);

        if (result == 0) {
            listener.getLogger().println("Vault file created: " + vaultName);
        } else {
            throw new Exception("Failed to create vault file: " + vaultName + " (exit code: " + result + ")");
        }
    }

    /** Setup project files (checkout, ansible.cfg) */
    private void setupProject(Launcher launcher, TaskListener listener) throws Exception {
        listener.getLogger().println("Setting up project files...");

        // Create project directory
        container.execute("mkdir -p " + projectRoot, launcher, listener);

        // Checkout project
        checkoutProject(launcher, listener);

        // Generate and write ansible.cfg
        setupAnsibleConfig(launcher, listener);
    }

    /** Checkout project from repository */
    private void checkoutProject(Launcher launcher, TaskListener listener) throws Exception {
        // Simple git clone - in real implementation you'd integrate with your RepoCacheManager
        String checkoutCmd = String.format(
                "cd %s && git clone %s . || (git fetch && git reset --hard origin/%s)",
                projectRoot, project.getRepository(), version.get("ref"));

        container.execute(checkoutCmd, launcher, listener);
        listener.getLogger().println("Project checked out: " + project.getRepository() + "@" + version.get("ref"));
    }

    /** Setup ansible.cfg file */
    private void setupAnsibleConfig(Launcher launcher, TaskListener listener) throws Exception {
        String ansibleConfig = project.getAnsibleConfig();

        String configCmd = String.format("cat > %s/ansible.cfg << 'EOF'\n%s\nEOF", projectRoot, ansibleConfig);

        container.execute(configCmd, launcher, listener);
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
                .user(user != null ? user : "root")
                .inventory(getInventoryPath(envName))
                .projectRoot(projectRoot)
                .extraVars(extraVars)
                .options(options);

        // Add vault configurations
        for (String vaultName : setupVaultCredentials.keySet()) {
            builder.vault(vaultName, vaultDir + "/" + vaultName + ".txt");
        }

        // Log command summary
        listener.getLogger().println("=== Executing Ansible Playbook ===");
        listener.getLogger().println(builder.getSummary());

        // Build and execute command
        String fullCmd = builder.buildFullCommand();
        return container.execute(fullCmd, "root", launcher, listener);
    }

    /** Execute a command in the project environment */
    public int executeCommand(String command, Launcher launcher, TaskListener listener) throws Exception {
        ensureInitialized();

        if (isKilled) {
            throw new IllegalStateException("AnsibleContext has been killed");
        }

        String fullCmd = "cd " + projectRoot + " && " + command;
        if (sshAgent != null && sshAgent.getSocketPath() != null) {
            fullCmd = "export SSH_AUTH_SOCK=" + sshAgent.getSocketPath() + " && " + fullCmd;
        }

        return container.execute(fullCmd, "root", launcher, listener);
    }

    /** Release reference - similar to ContainerManager.release */
    public void release(boolean cleanup, Launcher launcher, TaskListener listener) {
        synchronized (AnsibleContext.class) {
            referenceCount--;

            if (referenceCount <= 0 && cleanup) {
                listener.getLogger().println("Cleaning up Ansible context: " + projectId);
                kill(launcher, listener);
                activeContexts.remove(contextKey);
            } else if (referenceCount <= 0) {
                listener.getLogger().println("Keeping Ansible context alive: " + projectId);
            }
        }
    }

    /** Full cleanup */
    private void kill(Launcher launcher, TaskListener listener) {
        if (isKilled) return;

        try {
            // 2. Release SSH keys
            if (sshAgent != null) {
                sshAgent.stop(launcher, listener);
            }

            // 3. Release container (this will call ContainerManager.release)
            if (container != null) {
                container.release(true, launcher, listener); // cleanup=true
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
        return !isKilled && container != null && !container.isKilled();
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

    /** Get secret credential value */
    private String getSecretCredential(String credentialId, Run<?, ?> run) {
        try {
            StringCredentials stringCredential = CredentialsProvider.findCredentialById(credentialId, StringCredentials.class, run);
            if (stringCredential == null) {
                throw new AbortException("Credential for vault not found, make sure it is configured in Jenkins");
            }
            return Secret.toString(stringCredential.getSecret());
        } catch (Exception e) {
            return null;
        }
    }

    /** Cleanup all contexts */
    public static void cleanupAll(Launcher launcher, TaskListener listener) {
        synchronized (AnsibleContext.class) {
            List<AnsibleContext> contexts = new ArrayList<>(activeContexts.values());

            for (AnsibleContext context : contexts) {
                try {
                    context.kill(launcher, listener);
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

    public Map<String, String> getVersion() {
        return version;
    }

    public AnsibleProject getProject() {
        return project;
    }

    public String getProjectRoot() {
        return projectRoot;
    }

    public String getNodeName() {
        return nodeName;
    }

    public ContainerManager getContainer() {
        return container;
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
        return setupVaultCredentials.keySet();
    }
}
