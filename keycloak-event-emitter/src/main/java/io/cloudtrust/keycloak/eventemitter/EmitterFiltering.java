package io.cloudtrust.keycloak.eventemitter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudtrust.exception.CloudtrustRuntimeException;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.admin.AdminEvent;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

public class EmitterFiltering {
    private static final Logger logger = Logger.getLogger(EmitterFiltering.class);
    private static final String EMITTER_FILTERING_ENV_VAR = "EVENTS_EMITTER_FILTERING";

    private EventFiltering eventFiltering;
    private AdminEventFiltering adminEventFiltering;
    private final Set<String> deniedSet = new ConcurrentSkipListSet<>();

    public EventFiltering getEventFiltering() {
        return eventFiltering;
    }
    public void setEventFiltering(EventFiltering eventFiltering) {
        this.eventFiltering = eventFiltering;
    }

    public AdminEventFiltering getAdminEventFiltering() {
        return adminEventFiltering;
    }
    public void setAdminEventFiltering(AdminEventFiltering adminEventFiltering) {
        this.adminEventFiltering = adminEventFiltering;
    }

    public static EmitterFiltering create() {
        return create(System.getenv(EMITTER_FILTERING_ENV_VAR));
    }

    public static EmitterFiltering create(String json) {
        if (StringUtils.isBlank(json)) {
            throw new CloudtrustRuntimeException("Emitter filtering configuration is missing");
        }
        try {
            var res = new ObjectMapper().readValue(json, EmitterFiltering.class);
            res.validate();
            return res;
        } catch (IOException ioe) {
            throw new CloudtrustRuntimeException("Failed to parse emitter filtering configuration", ioe);
        }
    }

    private void validate() {
        if (eventFiltering==null) {
            throw new CloudtrustRuntimeException("Emitter filtering configuration is missing the event filtering part");
        } else {
            eventFiltering.validate();
        }
        if (adminEventFiltering!=null) {
            adminEventFiltering.validate();
        }
    }

    public boolean passes(Event event) {
        boolean res = eventFiltering == null || eventFiltering.passes(event);
        if (!res) {
            logDenied(event.getType().toString());
        }
        return res;
    }

    public boolean passes(AdminEvent event) {
        boolean res = adminEventFiltering == null || adminEventFiltering.passes(event);
        if (!res) {
            logDenied(event.getResourceType().toString());
        }
        return res;
    }

    private void logDenied(String key) {
        if (deniedSet.add(key)) {
            // Use same "Ignoring unsupported event" as in back-lifecycle-service to make it easier to find all unsupported events in OpenSearch
            logger.infof("Did not pass filtering. Ignoring unsupported event with key %s", key);
        }
    }

    public record EventFiltering(Set<String> typeBlacklist, Set<String> typeWhitelist) {
        private void validate() {
            if (typeWhitelist!=null && typeWhitelist.isEmpty()) {
                throw new CloudtrustRuntimeException("Event filtering configuration cannot have an empty whitelist");
            }
            var emptyBlacklist = typeBlacklist==null || typeBlacklist.isEmpty();
            if (!emptyBlacklist && typeWhitelist!=null) {
                throw new CloudtrustRuntimeException("Event filtering configuration cannot have both a non-empty whitelist and a non-empty blacklist");
            }
            if (emptyBlacklist && typeWhitelist==null) {
                throw new CloudtrustRuntimeException("Event filtering configuration must have either a non-empty whitelist or a non-empty blacklist");
            }
        }

        public boolean passes(Event event) {
            var passBlackList = typeBlacklist==null || !typeBlacklist.contains(event.getType().toString());
            var passWhiteList = typeWhitelist==null || typeWhitelist.contains(event.getType().toString());
            return passBlackList && passWhiteList;
        }
    }

    public record AdminEventFiltering(Set<String> resourceTypeBlacklist, Set<String> resourceTypeWhitelist) {
        private void validate() {
            if (resourceTypeWhitelist!=null && resourceTypeWhitelist.isEmpty()) {
                throw new CloudtrustRuntimeException("AdminEvent filtering configuration cannot have an empty whitelist");
            }
            var emptyBlacklist = resourceTypeBlacklist==null || resourceTypeBlacklist.isEmpty();
            if (!emptyBlacklist && resourceTypeWhitelist!=null) {
                throw new CloudtrustRuntimeException("AdminEvent filtering configuration cannot have both a non-empty whitelist and a non-empty blacklist");
            }
            if (emptyBlacklist && resourceTypeWhitelist==null) {
                throw new CloudtrustRuntimeException("AdminEvent filtering configuration must have either a non-empty whitelist or a non-empty blacklist");
            }
        }

        public boolean passes(AdminEvent adminEvent) {
            var passBlackList = resourceTypeBlacklist==null || !resourceTypeBlacklist.contains(adminEvent.getResourceType().toString());
            var passWhiteList = resourceTypeWhitelist==null || resourceTypeWhitelist.contains(adminEvent.getResourceType().toString());
            return passBlackList && passWhiteList;
        }
    }
}
