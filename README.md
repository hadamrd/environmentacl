# Environment ACL Manager Plugin

Jenkins plugin for environment-based access control with fine-grained permissions management.

## Features

- **Environment Choice Parameter** - Dynamic dropdown showing only accessible environments
- **Pipeline Access Control** - `checkEnvironmentACL()` step for runtime permission checks
- **Flexible Rules** - User/group-based access with regex job patterns and environment tags
- **Credential Integration** - Automatic SSH and Vault credential mapping per environment
- **JCasC Support** - Full Configuration as Code integration
- **Management UI** - View current configuration and rules

## Quick Setup

### 1. Install Plugin
Install from Jenkins Plugin Manager or build from source.

### 2. Configure via JCasC

```yaml
unclassified:
  environmentACL:
    environmentGroups:
      - name: "development"
        environments: ["dev1", "dev2", "staging"]
        tags: ["development", "testing"]
        sshCredentialId: "dev-ssh-key"
        vaultCredentials:
          - vaultId: "dev-vault"
            credentialId: "dev-vault-token"
      
      - name: "production"
        environments: ["prod1", "prod2"]
        tags: ["production", "critical"]
        sshCredentialId: "prod-ssh-key"
    
    aclRules:
      - name: "developers-access"
        type: "allow"
        priority: 200
        jobs: ["build-.*", "test-.*"]
        environmentGroups: ["development"]
        users: ["developer1", "developer2"]
        
      - name: "sre-access"
        type: "allow"
        priority: 300
        jobs: ["deploy-.*", "release-.*"]
        environments: ["*"]
        users: ["sre-team"]
```

### 3. View Configuration
Navigate to **Manage Jenkins** → **Environment ACL Manager** to view loaded configuration.

## Usage

### Environment Choice Parameter

Add to your job/pipeline:

```groovy
pipeline {
    agent any
    parameters {
        environmentChoice(
            name: 'TARGET_ENV',
            description: 'Select target environment'
        )
    }
    stages {
        stage('Deploy') {
            steps {
                echo "Deploying to: ${params.TARGET_ENV}"
            }
        }
    }
}
```

### Pipeline Access Check

```groovy
stage('Environment Check') {
    steps {
        script {
            def result = checkEnvironmentACL('prod1')
            
            if (result.hasAccess) {
                echo "Access granted to: ${result.environment}"
                echo "SSH Key: ${result.sshCredentialId}"
                echo "Vault Credentials: ${result.vaultCredentials}"
                
                // Use credentials for deployment
                sshagent([result.sshCredentialId]) {
                    sh "deploy.sh ${result.environment}"
                }
            } else {
                error("Access denied: ${result.errorMessage}")
            }
        }
    }
}
```

## Configuration Reference

### Environment Groups

```yaml
environmentGroups:
  - name: "group-name"
    description: "Optional description"
    environments: ["env1", "env2"]          # Environment names
    tags: ["tag1", "tag2"]                  # Optional tags for filtering
    sshCredentialId: "ssh-key-id"           # SSH credential for this group
    vaultCredentials:                       # Vault credential mappings
      - vaultId: "vault-name"
        credentialId: "vault-token-id"
```

### ACL Rules

```yaml
aclRules:
  - name: "rule-name"
    type: "allow"                           # "allow" or "deny"
    priority: 200                           # Higher = higher priority
    jobs: ["build-.*", "deploy-.*"]         # Regex patterns for job names
    environments: ["prod1"]                 # Specific environments
    environmentGroups: ["production"]       # Or environment groups
    environmentTags: ["critical"]           # Or by tags
    users: ["user1", "user2"]               # User IDs
    groups: ["group1", "group2"]            # User groups
```

### Rule Matching

- **Jobs**: Regex patterns (`deploy-.*` matches `deploy-prod`, `deploy-staging`)
- **Priority**: Higher numbers processed first
- **Deny vs Allow**: Deny rules checked first, then allow rules
- **Wildcards**: Use `*` for "all" in any field

## Access Patterns

### Development Team
```yaml
- name: "dev-team-access"
  type: "allow"
  jobs: ["build-.*", "test-.*"]
  environmentTags: ["development"]
  users: ["developer1", "developer2"]
```

### Production Deployment
```yaml
- name: "prod-deployment"
  type: "allow"  
  jobs: ["deploy-.*", "release-.*"]
  environmentTags: ["production"]
  users: ["release-manager"]
```

### Security Boundary
```yaml
- name: "block-prod-access"
  type: "deny"
  priority: 999
  environmentTags: ["production"]
  users: ["contractor", "intern"]
```

## Troubleshooting

### Empty Environment List
- Check user has matching ACL rules
- Verify job name matches rule patterns
- Check rule priority order (deny vs allow)

### Script Console Testing
```groovy
import io.jenkins.plugins.environmentacl.service.EnvironmentACLChecker

// Test access for specific user/job/environment
def hasAccess = EnvironmentACLChecker.hasAccess("username", [], "job-name", "environment")
println "Access: " + hasAccess

// Get accessible environments for job
def environments = EnvironmentACLChecker.getAccessibleEnvironments("job-name")
println "Accessible: " + environments
```

## Requirements

- Jenkins 2.462.3+
- Java 11+
- Configuration as Code plugin (recommended)

## License

MIT License