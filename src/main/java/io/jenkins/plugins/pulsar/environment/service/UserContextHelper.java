package io.jenkins.plugins.pulsar.environment.service;

import hudson.model.Cause;
import hudson.model.Run;
import hudson.model.User;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

public final class UserContextHelper {

    private UserContextHelper() {}

    public static class UserContext {
        private final String userId;
        private final List<String> groups;

        public UserContext(String userId, List<String> groups) {
            this.userId = userId;
            this.groups = groups != null ? groups : new ArrayList<>();
        }

        public String getUserId() {
            return userId;
        }

        public List<String> getGroups() {
            return groups;
        }
    }

    /** Get user context from current authentication (for UI/parameters) */
    public static UserContext getCurrentUserContext() {
        Authentication auth = Jenkins.getAuthentication2();
        if (auth == null) {
            return new UserContext("anonymous", new ArrayList<>());
        }

        String userId = auth.getName() != null ? auth.getName() : "anonymous";
        List<String> groups = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        return new UserContext(userId, groups);
    }

    /**
     * Get user context from build run (for pipeline steps) Falls back to current auth if no build
     * cause user found
     */
    public static UserContext getUserContextFromRun(Run<?, ?> run) {
        if (run == null) {
            return getCurrentUserContext();
        }

        // Try to get user from build cause first
        Cause.UserIdCause userCause = run.getCause(Cause.UserIdCause.class);
        if (userCause != null) {
            String userId = userCause.getUserId();
            if (userId != null && !"SYSTEM".equals(userId)) {
                List<String> groups = getUserGroups(userId);
                return new UserContext(userId, groups);
            }
        }

        // Fallback to current authentication
        return getCurrentUserContext();
    }

    /** Get user groups for a specific user ID (for build cause users) */
    private static List<String> getUserGroups(String userId) {
        try {
            User user = Jenkins.get().getUser(userId);
            if (user != null) {
                return user.getAuthorities();
            }
        } catch (Exception e) {
            // User might not exist in local database for external auth
        }
        return new ArrayList<>();
    }
}
