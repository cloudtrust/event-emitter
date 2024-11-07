package flatbuffers.events;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.keycloak.events.EventType;

public class EventTypeTest {

    /**
     * This test checks that the list of EventType defined in flatbuffers matches the list defined by the current
     * Keycloak version. If it is not the case, it means that the file events.fbs needs to be updated according
     * to the new EventType file defined in the root GitHub repository of the correct version.
     */
    @Test
    void testEventTypesAreUpToDate() {
        EventType[] updatedEventTypes = EventType.values();
        String[] flatbufferEventTypes = flatbuffers.events.EventType.names;

        for (int i = 0; i < updatedEventTypes.length; i++) {
            Assertions.assertEquals(updatedEventTypes[i].name(), flatbufferEventTypes[i]);
        }
    }
}
