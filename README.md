# Jenkins Jerakin Deployment Framework

A comprehensive Jenkins plugin that provides configuration-driven deployment automation with environment-based access controls, job templating, and infrastructure orchestration. This plugin enables teams to define reusable deployment patterns while maintaining security and infrastructure isolation.

## Features

- **Configuration-as-Code**: Define entire deployment workflows using JCaS YAML
- **Job Templates**: Create reusable deployment patterns with parameterized scripts
- **Environment Access Control**: ACL-based restrictions with user/group permissions
- **Dynamic Infrastructure Selection**: Automatic node selection based on environment mapping
- **Parameter Precedence**: UI parameters can be overridden by job-specific configuration
- **Ansible Integration**: Built-in support for containerized Ansible execution
- **Credential Management**: Environment-specific SSH keys and vault credentials

## Architecture

The plugin operates on three main concepts:

1. **Environment Groups**: Define infrastructure topology and access credentials
2. **Job Templates**: Reusable deployment patterns with parameterized execution
3. **Deployment Jobs**: Specific instances that reference templates with custom parameters

## Configuration

### Environment Groups & Access Control

Define environment groups with associated infrastructure and credentials:

```yaml
unclassified:
  environmentACL:
    environmentGroups:
      - name: "production"
        description: "Production environments"
        environments:
          - "prod-eu"
          - "prod-us"
        nodeLabels:
          - "prod-agent"
        sshCredentialId: "prod-ssh-key"
        vaultCredentials:
          - vaultId: "prod"
            credentialId: "prod-vault-key"
        tags:
          - "production"
          - "critical"
      
      - name: "development"
        description: "Development environments"
        environments:
          - "dev"
          - "staging"
        nodeLabels:
          - "dev-agent"
        sshCredentialId: "dev-ssh-key"

    aclRules:
      - name: "sre-production-access"
        type: "allow"
        priority: 300
        jobs: ["*"]
        environmentGroups: ["production"]
        users: ["sre-team"]
      
      - name: "developers-dev-access"
        type: "allow"
        priority: 200
        jobs: ["*"]
        environmentGroups: ["development"]
        users: ["dev-team"]
```

### Ansible Project Configuration

Configure Ansible projects with environment-specific inventory mapping:

```yaml
unclassified:
  ansibleProjects:
    projects:
      - id: "infrastructure"
        repository: "https://github.com/company/ansible-infrastructure"
        defaultBranch: "main"
        execEnv: "local/ansible:latest"
        envGroups:
          - groupName: "production"
            inventoryPathTemplate: "inventory/prod"
            vaultIds: ["prod"]
          - groupName: "development"
            inventoryPathTemplate: "inventory/dev"
            vaultIds: ["dev"]
        vaults:
          - id: "prod"
            credentialId: "prod-ansible-vault"
          - id: "dev"
            credentialId: "dev-ansible-vault"
```

### Job Templates & Deployment Jobs

Define reusable templates and specific job instances:

```yaml
unclassified:
  pulsarDeployments:
    templates:
      - name: "ansible-deployment"
        description: "Standard Ansible deployment template"
        params:
          - name: "environment"
            type: "environment"
            description: "Target environment"
          - name: "ref"
            type: "ansibleProjectRef"
            description: "Ansible project reference"
            properties:
              - name: "projectId"
                value: "infrastructure"
          - name: "playbook"
            type: "string"
            description: "Playbook to execute"
        script: |
          def deployParams = resolveDeployParams(jobId: env.JOB_BASE_NAME)
          
          node(deployParams.nodeLabels) {
            ansibleProject(projectId: 'infrastructure', ref: deployParams.ref) {
              ansiblePlaybook(
                user: 'ansible',
                playbook: deployParams.playbook,
                envName: deployParams.environment
              )
            }
          }

    jobs:
      - id: "deploy-webservers"
        name: "Deploy Web Servers"
        category: "Infrastructure"
        templateName: "ansible-deployment"
        params:
          - name: "playbook"
            value: "webserver.yml"  # Fixed parameter
      
      - id: "deploy-databases"
        name: "Deploy Database Cluster"
        category: "Infrastructure"
        templateName: "ansible-deployment"
        params:
          - name: "playbook"
            value: "database.yml"
          - name: "ref"
            value: "stable"  # Force stable branch
```

## Parameter Resolution & Precedence

The framework resolves parameters with the following precedence (highest to lowest):

1. **Job-level fixed parameters** (defined in job config)
2. **Step configuration** (passed to `resolveDeployParams`)
3. **UI parameters** (filled by user when running the job)

### Example Resolution

For a job with this configuration:
```yaml
params:
  - name: "playbook"
    value: "webserver.yml"  # Fixed by job config
```

And a template with these parameters:
```yaml
params:
  - name: "environment"
    type: "environment"
  - name: "playbook"
    type: "string"
```

**Result:**
- User sees only "environment" parameter in UI (playbook is fixed)
- `resolveDeployParams` returns `{environment: "prod-eu", playbook: "webserver.yml"}`
- Template script uses `deployParams.playbook` which is always "webserver.yml"

## Generated Jobs

The plugin automatically creates Jenkins jobs based on your configuration:

```
projects/
├── Infrastructure/
│   ├── PulsarJob_deploy-webservers    # Only shows 'environment' parameter
│   └── PulsarJob_deploy-databases     # Shows 'environment' only (ref fixed to 'stable')
└── Applications/
    └── PulsarJob_app-deployment
```

Each job:
- Shows only non-fixed template parameters as build parameters
- Runs on appropriate nodes based on environment selection
- Has access to environment-specific credentials
- Executes the template script with resolved parameters

## Pipeline Steps

### `resolveDeployParams`

Resolves deployment parameters with proper precedence and infrastructure context.

```groovy
def deployParams = resolveDeployParams(jobId: 'deploy-webservers')

// Returns:
// {
//   environment: "prod-eu",
//   playbook: "webserver.yml",
//   nodeLabels: "prod-agent",
//   ref: "main"
// }
```

### `ansibleProject`

Creates isolated Ansible execution environment:

```groovy
ansibleProject(projectId: 'infrastructure', ref: deployParams.ref) {
    ansiblePlaybook(
        user: 'ansible',
        playbook: deployParams.playbook,
        envName: deployParams.environment
    )
}
```

### `checkEnvironmentACL`

Validates environment access and provides credential information:

```groovy
def aclResult = checkEnvironmentACL(deployParams.environment)
// Returns access status, SSH credentials, vault mappings
```

## Security Model

### Access Control
- Users only see environments they have permission to access
- Environment parameters are filtered based on ACL rules
- Access denials are logged for security auditing

### Credential Management
- SSH keys are environment-specific and managed through Jenkins credentials
- Ansible vault passwords are mapped per environment group
- No credentials are exposed in pipeline logs

### Infrastructure Isolation
- Jobs run on environment-appropriate nodes based on labels
- Container isolation for Ansible execution
- Environment-specific inventory and configuration

## Use Cases

### Standard Infrastructure Deployment
```yaml
# Template defines the pattern
templates:
  - name: "infrastructure"
    params:
      - name: "environment"
        type: "environment"
      - name: "component"
        type: "choice"
        properties:
          - name: "choices"
            value: "webserver,database,loadbalancer"

# Jobs customize for specific components
jobs:
  - id: "deploy-webserver"
    templateName: "infrastructure"
    params:
      - name: "component"
        value: "webserver"  # Users only select environment
```

### Application Deployment with Version Control
```yaml
templates:
  - name: "app-deployment"
    params:
      - name: "version"
        type: "string"
        description: "Application version"
      - name: "environment"
        type: "environment"

jobs:
  - id: "deploy-api-prod"
    templateName: "app-deployment"
    params:
      - name: "environment"
        value: "prod-eu"  # Lock to production
```

### Multi-Environment Rollout
```yaml
jobs:
  - id: "rollout-feature"
    templateName: "app-deployment"
    # No fixed params - users select any environment and version
```

## Benefits

1. **Standardization**: Templates ensure consistent deployment patterns
2. **Security**: Environment-based access control with credential isolation
3. **Flexibility**: Parameter precedence allows customization without duplication
4. **Maintainability**: Configuration-as-code approach with centralized management
5. **Scalability**: One template can generate dozens of specialized jobs
6. **Compliance**: Built-in access logging and environment restrictions

## Migration from Legacy Jobs

Replace manual job creation with template-based configuration:

**Before:**
- 50 manually created Jenkins jobs
- Copy-paste configuration with subtle differences
- Manual credential management

**After:**
- 3 job templates
- 50 job definitions in JCaS YAML
- Automatic credential and infrastructure mapping

The framework transforms deployment management from job-centric to pattern-centric, enabling teams to focus on deployment logic rather than Jenkins configuration.