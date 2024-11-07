package flatbuffers.events;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.keycloak.events.admin.OperationType;

public class OperationTypeTest {
    /**
     * This test checks that the list of OperationType defined in flatbuffers matches the list defined by the current
     * Keycloak version. If it is not the case, it means that the file events.fbs needs to be updated according
     * to the new OperationType file defined in the root GitHub repository of the correct version.
     */
    @Test
    void testOperationTypesAreUpToDate() {
        OperationType[] updatedOperationTypes = OperationType.values();
        String[] flatbufferOperationTypes = flatbuffers.events.OperationType.names;

        for (int i = 0; i < updatedOperationTypes.length; i++) {
            Assertions.assertEquals(updatedOperationTypes[i].name(), flatbufferOperationTypes[i]);
        }
    }
}
