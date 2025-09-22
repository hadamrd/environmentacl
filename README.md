# Jenkins Pulsar Ansible Plugin

A Jenkins plugin that provides configuration-driven Ansible automation with isolated, volatile execution environments. This plugin enables teams to run Ansible playbooks within containerized environments while maintaining security through ACL-based access controls.

## Features

- **Isolated Execution Environments**: Each Ansible execution runs in a dedicated Docker container with managed SSH agents
- **Configuration-Driven Pipelines**: Define Ansible jobs using simple configuration syntax
- **Environment Access Control**: ACL-based restrictions on environment access per user/team
- **Project Reference Management**: Version-controlled Ansible project references with dynamic selection
- **Persistent vs Volatile Modes**: Choose between container cleanup or reuse for performance optimization
- **Automated Resource Management**: Automatic discovery and cleanup of orphaned containers and SSH agents

## Architecture

The plugin manages two key components:

1. **Container Manager**: Handles shared Docker containers for Ansible execution
2. **SSH Agent Manager**: Manages SSH key distribution and agent lifecycle

Both components support automatic discovery on Jenkins startup to adopt orphaned resources back into memory management.

## Installation

1. Install the plugin through Jenkins Plugin Manager
2. Ensure Docker is available on Jenkins agents
3. Configure appropriate permissions for Jenkins to manage Docker containers

## Configuration

### Basic Pipeline Job

```groovy
pipelineJob('ansible-job') {
  parameters {
    environmentChoice {
      name 'environment'
      description 'Select the target environment'
    }
    ansibleProjectRef {
      name 'ref'
      description 'Select ansible project ref'
      projectId 'ansible'
    }
  }
  definition {
    cps {
      script('''
      node('dev') {
        ansibleProject(projectId: 'ansible', ref: params.ref, cleanup: false) {
            ansiblePlaybook(
                user: 'ansible',
                playbook: 'playbooks/ping.yml',
                envName: params.environment
            )
        }
      }'''.stripIndent())
      sandbox()
    }
  }
}
```

## Pipeline Steps

### `ansibleProject`

Creates an isolated execution environment for Ansible operations.

**Parameters:**
- `projectId` (String): Identifier for the Ansible project
- `ref` (String): Git reference (branch, tag, commit) to checkout
- `cleanup` (Boolean): Whether to destroy the environment after execution
  - `true`: Volatile mode - container and SSH agent are cleaned up
  - `false`: Persistent mode - resources remain for reuse

**Example:**
```groovy
ansibleProject(projectId: 'myproject', ref: 'main', cleanup: true) {
    // Ansible operations here
}
```

### `ansiblePlaybook`

Executes an Ansible playbook within the project environment.

**Parameters:**
- `playbook` (String): Path to the playbook file
- `user` (String): SSH user for connections
- `envName` (String): Target environment name
- Additional Ansible options as needed

**Example:**
```groovy
ansiblePlaybook(
    user: 'deploy',
    playbook: 'site.yml',
    envName: 'production',
    extraVars: [
        'app_version': '1.2.3'
    ]
)
```

## Parameter Types

### `environmentChoice`

Provides a dropdown selection of available environments, filtered by user permissions.

```groovy
environmentChoice {
    name 'environment'
    description 'Select target environment'
}
```

### `ansibleProjectRef`

Provides a dropdown selection of available project references (branches, tags).

```groovy
ansibleProjectRef {
    name 'ref'
    description 'Select project version'
    projectId 'ansible'
}
```

## Access Control

The plugin implements ACL-based environment access control:

- Users only see environments they have permission to access
- Environment choices are dynamically filtered based on user credentials
- Failed access attempts are logged for security auditing

## Resource Management

### Container Management

- Containers are labeled for tracking and discovery
- Automatic cleanup of orphaned containers on Jenkins startup
- Shared containers across multiple executions when `cleanup: false`

### SSH Agent Management

- One SSH agent per Jenkins node (singleton pattern)
- Automatic discovery and adoption of orphaned agents
- Reference counting for key management
- Automatic cleanup of zombie processes and orphaned socket files

## Performance Considerations

### Persistent Mode (`cleanup: false`)

**Advantages:**
- Faster subsequent executions
- Reduced Docker overhead
- Persistent SSH connections

**Use Cases:**
- Development environments
- Frequent playbook executions
- Performance-critical pipelines

### Volatile Mode (`cleanup: true`)

**Advantages:**
- Clean state for each execution
- No resource leaks
- Better isolation

**Use Cases:**
- Production deployments
- Compliance requirements
- One-off executions

## Troubleshooting

### Container Issues

Check for orphaned containers:
```bash
docker ps --filter "label=io.jenkins.sharedcontainer.managed=true"
```

### SSH Agent Issues

Check for active SSH agents:
```bash
find /tmp/jenkins-ssh-agents -name "*.sock"
```

### Plugin Logs

Monitor Jenkins logs for discovery and cleanup activities:
- Container discovery: `io.jenkins.plugins.pulsar.container.service`
- SSH agent discovery: `io.jenkins.plugins.pulsar.ssh.service`

## Advanced Configuration

### Custom Container Images

Configure base images for different Ansible environments:

```groovy
ansibleProject(
    projectId: 'ansible',
    ref: params.ref,
    image: 'custom/ansible:latest',
    cleanup: false
) {
    // operations
}
```

### Environment-Specific Settings

Override settings per environment:

```groovy
ansiblePlaybook(
    playbook: 'deploy.yml',
    envName: params.environment,
    vaultPasswordFile: "/secrets/${params.environment}/vault-pass"
)
```

## Security Considerations

- SSH keys are managed securely through Jenkins credentials
- Container isolation prevents cross-environment access
- ACL enforcement at the parameter level
- Audit logging for all environment access

## Contributing

The plugin is built with resource discovery and cleanup in mind. When extending functionality, ensure proper labeling for containers and cleanup of temporary resources.

## License

[Your License Here]