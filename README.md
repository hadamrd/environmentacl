# Jenkins Configuration as Code (JCasC) Support

The Environment ACL Manager plugin fully supports Jenkins Configuration as Code, allowing you to manage your environment groups and ACL rules through YAML configuration files.

## Benefits

- **Version Control**: Store your ACL configuration in Git alongside your infrastructure code
- **Reproducibility**: Easily replicate Jenkins configurations across environments
- **Automation**: Deploy Jenkins with pre-configured ACL rules
- **Disaster Recovery**: Quickly restore Jenkins configuration from code

## Configuration Structure

The plugin configuration is placed under the `unclassified` section with the key `environmentACL`:

```yaml
unclassified:
  environmentACL:
    environmentGroups:
      - name: "group-name"
        description: "Group description"
        environments:
          - "env1"
          - "env2"
        sshKeys:
          - "ssh-key-id"
        vaultKeys:
          - "vault-key-id"
        nodeLabels:
          - "label1"
    
    rules:
      - name: "rule-name"
        type: "allow" # or "deny"
        priority: 100
        jobs:
          - "job-pattern"
        environments:
          - "env-name"
        envCategories:
          - "group-name"
        users:
          - "username"
        groups:
          - "group-name"
```

## Complete Example

```yaml
jenkins:
  systemMessage: "Jenkins with Environment ACL Manager"

unclassified:
  environmentACL:
    environmentGroups:
      # Production environments with restricted access
      - name: "production"
        description: "Production environments - restricted access"
        environments:
          - "prod-us-east-1"
          - "prod-us-west-2"
          - "prod-eu-central-1"
        sshKeys:
          - "prod-deploy-key"
          - "prod-admin-key"
        vaultKeys:
          - "prod-vault-token"
        nodeLabels:
          - "production"
          - "high-security"
          - "linux"
      
      # Staging for testing
      - name: "staging"
        description: "Staging environments for testing"
        environments:
          - "staging-main"
          - "staging-perf"
        sshKeys:
          - "staging-deploy-key"
        vaultKeys:
          - "staging-vault-token"
        nodeLabels:
          - "staging"
          - "linux"
      
      # Development environments
      - name: "development"
        description: "Development environments"
        environments:
          - "dev-shared"
          - "dev-integration"
        sshKeys:
          - "dev-key"
        vaultKeys:
          - "dev-vault-token"
        nodeLabels:
          - "development"
          - "linux"
    
    rules:
      # Admins have full access
      - name: "Administrators Full Access"
        type: "allow"
        priority: 100
        jobs:
          - "*"
        environments:
          - "*"
        groups:
          - "jenkins-admins"
      
      # DevOps team can deploy to all environments
      - name: "DevOps Team Deployment"
        type: "allow"
        priority: 90
        jobs:
          - "deploy/*"
          - "rollback/*"
        envCategories:
          - "production"
          - "staging"
          - "development"
        groups:
          - "devops-team"
      
      # Developers can deploy to dev and staging
      - name: "Developer Access"
        type: "allow"
        priority: 50
        jobs:
          - "*"
        envCategories:
          - "development"
          - "staging"
        groups:
          - "developers"
      
      # QA team access to staging
      - name: "QA Team Staging Access"
        type: "allow"
        priority: 60
        jobs:
          - "test/*"
          - "smoke-test/*"
          - "regression/*"
        envCategories:
          - "staging"
        groups:
          - "qa-team"
      
      # Release managers can deploy to production
      - name: "Release Manager Production"
        type: "allow"
        priority: 80
        jobs:
          - "production-release/*"
        envCategories:
          - "production"
        groups:
          - "release-managers"
      
      # Explicitly deny contractors from production
      - name: "Block Contractors from Production"
        type: "deny"
        priority: 95
        jobs:
          - "*"
        envCategories:
          - "production"
        groups:
          - "contractors"
          - "external-users"
      
      # Specific user access
      - name: "John Doe Special Access"
        type: "allow"
        priority: 70
        jobs:
          - "special-deploy"
        environments:
          - "prod-us-east-1"
        users:
          - "jdoe"
```

## Migration from UI Configuration

If you're currently using the UI-based YAML configuration, you can easily migrate to JCasC:

1. **Export current configuration**: Copy your YAML from the Environment ACL Manager UI
2. **Convert to JCasC format**: Wrap your configuration under `unclassified.environmentACL`
3. **Save as jenkins.yaml**: Add to your JCasC configuration file
4. **Test**: Apply the configuration and verify it works

## Using with Docker

```dockerfile
FROM jenkins/jenkins:lts
COPY jenkins.yaml /var/jenkins_home/casc_configs/
ENV CASC_JENKINS_CONFIG=/var/jenkins_home/casc_configs
```

## Using with Kubernetes (Helm)

```yaml
jenkins:
  controller:
    JCasC:
      configScripts:
        environment-acl: |
          unclassified:
            environmentACL:
              environmentGroups:
                - name: "production"
                  # ... rest of configuration
```

## Environment Variable Substitution

JCasC supports environment variable substitution:

```yaml
unclassified:
  environmentACL:
    environmentGroups:
      - name: "production"
        environments:
          - "${PROD_ENV_1}"
          - "${PROD_ENV_2}"
        sshKeys:
          - "${PROD_SSH_KEY_ID}"
```

## Validation

The plugin automatically validates the configuration when loaded. Check the Jenkins logs for any configuration errors:

```bash
docker logs jenkins | grep environmentACL
```

## Combining with Other Configurations

The Environment ACL configuration can be split into separate files:

**jenkins-base.yaml:**
```yaml
jenkins:
  systemMessage: "Jenkins Server"
```

**jenkins-acl.yaml:**
```yaml
unclassified:
  environmentACL:
    # ... your ACL configuration
```

Load multiple files by setting:
```bash
export CASC_JENKINS_CONFIG=/path/to/configs/
```

## Troubleshooting

1. **Configuration not loading**: Check Jenkins logs for parsing errors
2. **Rules not applying**: Verify priority order (higher numbers = higher priority)
3. **Missing credentials**: Ensure credential IDs match those defined in Jenkins
4. **Export current config**: Use Jenkins UI → Manage Jenkins → Configuration as Code → View Configuration