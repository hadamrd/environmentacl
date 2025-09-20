# Makefile for Jenkins Plugin Development
JENKINS_HOME = /data/jenkins
HPI_FILE = target/environmentacl.hpi
PLUGIN_NAME = deployment-dashboard
SSH_OPTS = -o LogLevel=ERROR -o StrictHostKeyChecking=accept-new -o ControlMaster=auto -o ControlPath=/tmp/ssh_mux_%h_%p_%r -o ControlPersist=10m
JENKINS_URL = http://your-jenkins-master-url:8080
JENKINS_USER = jenkins-admin
JENKINS_CLI_JAR = jenkins-cli.jar
JENKINS_CLI = java -jar $(JENKINS_CLI_JAR) -s $(JENKINS_URL) -auth $(JENKINS_USER):$(JENKINS_API_TOKEN)
JENKINS_USER = jenkins
JENKINS_GROUP = jenkins
JENKINS_HOST= your-jenkins-host

.PHONY: lint run build install brun deploy get-cli
.ONESHELL:
SHELL := /bin/bash

lint:
	mvn spotless:apply

run:
	@echo "Starting Jenkins in development mode..."
# 	@dos2unix .env
# 	@dos2unix .secrets
	export MAVEN_OPTS="--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.util.concurrent=ALL-UNNAMED --add-opens java.base/java.net=ALL-UNNAMED --add-opens java.base/java.text=ALL-UNNAMED --add-opens java.base/java.nio.file=ALL-UNNAMED --add-opens java.base/java.security=ALL-UNNAMED"
	@set -a && source .env && source .secrets && set +a && mvn -Dhost=0.0.0.0 -Dport=8080 hpi:run

build:
	@echo "Building the plugin..."
	mvn clean package -DskipTests

get-cli:
	@if [ ! -f $(JENKINS_CLI_JAR) ]; then \
		echo "Downloading Jenkins CLI..."; \
		curl -o $(JENKINS_CLI_JAR) $(JENKINS_URL)/jnlpJars/jenkins-cli.jar; \
	fi

deploy:
	@scp $(SSH_OPTS) $(HPI_FILE) $(JENKINS_HOST):/tmp/$(PLUGIN_NAME).hpi; \
	ssh $(SSH_OPTS) $(JENKINS_HOST) "sudo mv /tmp/$(PLUGIN_NAME).hpi $(JENKINS_HOME)/plugins/ && sudo chown $(JENKINS_USER):$(JENKINS_GROUP) $(JENKINS_HOME)/plugins/$(PLUGIN_NAME).hpi && sudo docker restart jenkins"

restart:
	@echo "Restarting Jenkins via API..."
	@curl -X POST -u $(JENKINS_USER):$(JENKINS_API_TOKEN) "$(JENKINS_URL)/safeRestart"
	@echo "Jenkins restarting... Please wait for it to come back online."

bdeploy: lint build deploy

install:
	@echo "Installing the plugin..."
	mvn clean install -DskipTests -Dspotbugs.skip=true

brun: build run

help:
	@echo "Available commands:"
	@echo "  make lint    - Run code linting (Spotless)"
	@echo "  make run     - Run Jenkins in development mode"
	@echo "  make build   - Build the plugin (skip tests)"
	@echo "  make install - Install the plugin (skip tests)"
	@echo "  make burn    - Build and then run Jenkins"
	@echo "  make help    - Show this help message"
	@echo "  make bdeploy  - Build, deploy, and restart Jenkins"