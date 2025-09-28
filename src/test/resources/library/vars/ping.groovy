Map call(Map config) {
	return [
		prompts: [],
		run: { params ->
            ansibleProject(projectId: 'ansible', ref: params.ref) {
                ansiblePlaybook(
                    user: 'ansible',
                    playbook: 'playbooks/ping.yml',
                    envName: params.environment
                )
            }
		}
	]
}