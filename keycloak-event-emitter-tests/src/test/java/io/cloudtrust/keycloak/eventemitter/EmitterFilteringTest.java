package io.cloudtrust.keycloak.eventemitter;

import io.cloudtrust.exception.CloudtrustRuntimeException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.ResourceType;

public class EmitterFilteringTest {
    @ParameterizedTest
    @ValueSource(strings = {"    ", "{", """
            {
                "eventFiltering": {
                    "typeWhitelist": []
                }
            }
            """, """
            {
                "eventFiltering": {
                    "typeBlacklist": ["one-event"],
                    "typeWhitelist": ["another-event"]
                }
            }
            """, """
            {
                "eventFiltering": { "typeWhitelist": [] },
                "adminEventFiltering": { "resourceTypeWhitelist": [] }
            }
            """, """
            {
                "eventFiltering": { "typeWhitelist": [] },
                "adminEventFiltering": {
                    "resourceTypeBlacklist": ["one-resource"],
                    "resourceTypeWhitelist": ["another-resource]
                }
            }
            """})
    void invalidConfiguration(String value) {
        Assertions.assertThrows(CloudtrustRuntimeException.class, () -> EmitterFiltering.create(value));
    }

    @Test
    void validEventWhitelistConfiguration() {
        var event = new Event();
        var filtering = EmitterFiltering.create("""
            {
                "eventFiltering": {
                    "typeWhitelist": ["LOGIN"]
                }
            }
            """);
        Assertions.assertNotNull(filtering);

        // Filtering should pass for events in the whitelist
        event.setType(EventType.LOGIN);
        Assertions.assertTrue(filtering.passes(event));

        // Filtering should not pass for events not in the whitelist
        event.setType(EventType.LOGOUT);
        Assertions.assertFalse(filtering.passes(event));
    }

    @Test
    void validAdminEventWhiteResourceTypeConfiguration() {
        var event = new AdminEvent();
        var filtering = EmitterFiltering.create("""
            {
                "eventFiltering": { "typeWhitelist": ["IGNORED-IN-THIS-TEST"] },
                "adminEventFiltering": {
                    "resourceTypeWhitelist": ["REALM"]
                }
            }
            """);
        Assertions.assertNotNull(filtering);

        // Filtering should pass for events in the whitelist
        event.setResourceType(ResourceType.REALM);
        Assertions.assertTrue(filtering.passes(event));

        // Filtering should not pass for events not in the whitelist
        event.setResourceType(ResourceType.ORGANIZATION_GROUP_MEMBERSHIP);
        Assertions.assertFalse(filtering.passes(event));
    }
}
