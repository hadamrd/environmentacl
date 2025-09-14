# Clean Architecture: No Duplication!

## The Minimal Setup

```
┌────────────────────────────────────────────────┐
│                  USER INTERFACES               │
├────────────────────────┬───────────────────────┤
│   Manage Jenkins       │   jenkins.yaml        │
│   └── Environment ACL  │   └── environmentACL: │
│       Manager (UI)     │       └── [config]    │
└───────────┬────────────┴──────────┬────────────┘
            │                        │
            ▼                        ▼
┌───────────────────────────────────────────────┐
│     EnvironmentACLGlobalConfiguration         │
│     - Just data storage & JCasC support       │
│     - NO UI, NO Jelly files needed!           │
└───────────────────────────────────────────────┘
```

## What Each Component Does

### 1. **EnvironmentACLManagementLink** (UI Only)
- ✅ Provides the management page in Jenkins
- ✅ All UI logic and YAML editing
- ✅ Export to JCasC format
- ✅ Validation
- **Location**: `/manage/environment-acl`

### 2. **EnvironmentACLGlobalConfiguration** (Data + JCasC Only)
- ✅ Data storage (load/save)
- ✅ JCasC entry point (`@Symbol`)
- ✅ Helper methods for other components
- ❌ NO UI methods
- ❌ NO Jelly files needed

### 3. **Model Classes** (Shared)
- ✅ Work for both UI and JCasC
- ✅ Simple POJOs with annotations

## File Structure (Minimal!)

```
src/main/
├── java/
│   └── io/jenkins/plugins/environmentacl/
│       ├── EnvironmentACLManagementLink.java     [UI handler]
│       ├── EnvironmentACLGlobalConfiguration.java [Data + JCasC]
│       ├── EnvironmentACLChecker.java            [Business logic]
│       └── model/
│           └── EnvironmentACLConfig.java         [Data models]
└── resources/
    └── io/jenkins/plugins/environmentacl/
        └── EnvironmentACLManagementLink/
            └── index.jelly                        [The ONLY UI file!]
```

**Note**: No need for GlobalConfiguration Jelly files!

## How It Works

### User edits via UI:
1. User goes to Manage Jenkins → Environment ACL Manager
2. Edits YAML in the textarea
3. ManagementLink validates and saves to GlobalConfiguration
4. GlobalConfiguration persists to disk

### User configures via JCasC:
1. User adds configuration to `jenkins.yaml`:
   ```yaml
   unclassified:
     environmentACL:
       environmentGroups:
         - name: production
           environments: [prod-1]
       rules:
         - name: Admin
           type: allow
   ```
2. JCasC finds `@Symbol("environmentACL")` on GlobalConfiguration
3. Uses `@DataBoundSetter` methods to configure
4. GlobalConfiguration persists to disk

### Both use the same storage!

## Why This is Clean

1. **No Duplication**: One UI (ManagementLink), one data store (GlobalConfiguration)
2. **Clear Separation**: UI logic in ManagementLink, data/JCasC in GlobalConfiguration
3. **Minimal Code**: Only what's needed, nothing more
4. **Full Features**: Both UI and JCasC work perfectly

## Comparison with Bloated Approach

### ❌ Bloated (What we avoided):
- GlobalConfiguration with UI (global.jelly)
- ManagementLink with UI (index.jelly)  
- Duplicate configuration methods
- Confusion about which UI to use
- More code to maintain

### ✅ Clean (What we have):
- ManagementLink = UI
- GlobalConfiguration = Data + JCasC
- Single source of truth
- Less code, less bugs

## Testing

```java
// Test UI
@Test
public void testManagementLink() {
    WebClient wc = j.createWebClient();
    HtmlPage page = wc.goTo("manage/environment-acl");
    // Test the UI...
}

// Test JCasC
@Test
@ConfiguredWithCode("test.yaml")
public void testJCasC() {
    EnvironmentACLGlobalConfiguration config = 
        EnvironmentACLGlobalConfiguration.get();
    assertNotNull(config.getEnvironmentGroups());
}
```

## Examples from Other Plugins

Many successful plugins use this pattern:

- **Script Security Plugin**: ManagementLink for UI, GlobalConfiguration for data
- **Job Configuration History**: ManagementLink for history view
- **Support Core Plugin**: ManagementLink for support bundle generation

## Summary

- **1 UI** (ManagementLink) 
- **1 Data Store** (GlobalConfiguration)
- **2 Ways to Configure** (UI or JCasC)
- **0 Duplication**

This is the cleanest, most maintainable approach! 🎯