def call() {
    node('dev') {
        ansibleProject(projectId: 'ansible', version: [ref: 'main', type: 'branch']) {
            ansiblePlaybook(
                playbook: 'playbooks/ping.yml',
                envName: 'prod-eu'
            )
        }
    }
}
