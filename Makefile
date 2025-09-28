# Enhanced Makefile for Jerakin Plugin Distribution
PLUGIN_VERSION = $(shell mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
PLUGIN_NAME = jerakin
RELEASE_DIR = releases
HPI_FILE = target/$(PLUGIN_NAME)-plugin.hpi

build:
	@echo "Building jerakin plugin version $(PLUGIN_VERSION)..."
	mvn clean package -DskipTests

# Release builds
release-build:
	@echo "Building release version $(PLUGIN_VERSION)..."
	mvn clean package -Dchangelist= -DskipTests -Dspotbugs.skip=true
	mkdir -p $(RELEASE_DIR)
	cp target/*.hpi $(RELEASE_DIR)/$(PLUGIN_NAME)-plugin-$(PLUGIN_VERSION).hpi
	@echo "Release artifact: $(RELEASE_DIR)/$(PLUGIN_NAME)-plugin-$(PLUGIN_VERSION).hpi"

github-release: release-build
	gh release create v$(PLUGIN_VERSION) $(RELEASE_DIR)/$(PLUGIN_NAME)-plugin-$(PLUGIN_VERSION).hpi \
		--title "Jerakin Plugin v$(PLUGIN_VERSION)" \
		--notes "Configuration-driven Jenkins deployment framework with job templating and environment access control."

# Update center generation
update-center:
	@cat > $(RELEASE_DIR)/update-center.json << EOF
	{
	  "plugins": {
	    "$(PLUGIN_NAME)": {
	      "buildDate": "$(shell date -Iseconds)",
	      "name": "$(PLUGIN_NAME)",
	      "version": "$(PLUGIN_VERSION)",
	      "url": "https://github.com/yourorg/$(PLUGIN_NAME)-plugin/releases/download/v$(PLUGIN_VERSION)/$(PLUGIN_NAME)-plugin-$(PLUGIN_VERSION).hpi"
	    }
	  }
	}
	EOF

release: lint release-build github-release update-center
	@echo "Jerakin plugin $(PLUGIN_VERSION) released!"

# Version management
version-bump:
	@read -p "Enter new version (current: $(PLUGIN_VERSION)): " version; \
	mvn versions:set -DnewVersion=$$version -DgenerateBackupPoms=false

help:
	@echo "Distribution commands:"
	@echo "  make release-build    - Build release version"
	@echo "  make github-release   - Create GitHub release"
	@echo "  make update-center    - Generate update center JSON"
	@echo "  make release          - Complete release workflow"
	@echo "  make version-bump     - Update version number"