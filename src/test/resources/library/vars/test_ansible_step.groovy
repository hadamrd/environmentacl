def call() {
    node('dev') {
        ansibleProject(projectId: 'ansible', version: [ref: 'main', type: 'branch']) {
            ansiblePlaybook(
                playbook: 'ping.yml',
                envName: 'prod-eu'
            )
        }
    }
}
