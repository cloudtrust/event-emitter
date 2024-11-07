package flatbuffers.events;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.keycloak.events.admin.ResourceType;

public class ResourceTypeTest {
    /**
     * This test checks that the list of ResourceType defined in flatbuffers matches the list defined by the current
     * Keycloak version. If it is not the case, it means that the file events.fbs needs to be updated according
     * to the new ResourceType file defined in the root GitHub repository of the correct version.
     */
    @Test
    void testResourceTypesAreUpToDate() {
        ResourceType[] updatedResourceTypes = ResourceType.values();
        String[] flatbufferResourceTypes = flatbuffers.events.ResourceType.names;

        for (int i = 0; i < updatedResourceTypes.length; i++) {
            Assertions.assertEquals(updatedResourceTypes[i].name(), flatbufferResourceTypes[i]);
        }
    }
}
