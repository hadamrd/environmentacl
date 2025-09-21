def call() {
    node('dev') {
        ansibleProject(projectId: 'ansible', version: [ref: 'main', type: 'branch'], cleanup: false) {
            ansiblePlaybook(
                user: 'ansible',
                playbook: 'playbooks/ping.yml',
                envName: 'prod-eu'
            )
        }
        ansibleProject(projectId: 'ansible', version: [ref: 'main', type: 'branch']) {
            ansiblePlaybook(
                user: 'ansible',
                playbook: 'playbooks/ping.yml',
                envName: 'prod-eu'
            )
        }
    }
}
