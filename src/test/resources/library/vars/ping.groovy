Map call() {
    ansibleProject(projectId: 'ansible', ref: params.ref) {
        ansiblePlaybook(
            user: 'ansible',
            playbook: 'playbooks/ping.yml',
            envName: params.environment
        )
    }
}