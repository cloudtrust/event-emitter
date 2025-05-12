package flatbuffers.events;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;

import java.util.Arrays;
import java.util.List;

public class TypesTest {
    protected <E extends Enum<E>> void validateNames(Class<E> enumClass, String[] flatbufferTypes) {
        List<String> enumNames = Arrays.stream(enumClass.getEnumConstants()).map(Enum::name).toList();
        int flatLen = flatbufferTypes.length - ("UNKNOWN".equals(flatbufferTypes[flatbufferTypes.length-1]) ? 1 : 0);
        for(int i=0; i<Math.max(enumNames.size(), flatLen); i++) {
            String valueEnum = i<enumNames.size() ? enumNames.get(i) : "<missing>";
            String valueFlatb = i<flatbufferTypes.length ? flatbufferTypes[i] : "<missing>";
            Assertions.assertEquals(valueEnum, valueFlatb);
        }
        Assertions.assertEquals(enumNames.size(), flatLen);
    }

    /**
     * This test checks that the list of EventType defined in flatbuffers matches the list defined by the current
     * Keycloak version. If it is not the case, it means that the file events.fbs needs to be updated according
     * to the new EventType file defined in the root GitHub repository of the correct version.
     */
    @Test
    void testEventTypesAreUpToDate() {
        validateNames(EventType.class, flatbuffers.events.EventType.names);
    }

    /**
     * This test checks that the list of OperationType defined in flatbuffers matches the list defined by the current
     * Keycloak version. If it is not the case, it means that the file events.fbs needs to be updated according
     * to the new OperationType file defined in the root GitHub repository of the correct version.
     */
    @Test
    void testOperationTypesAreUpToDate() {
        validateNames(OperationType.class, flatbuffers.events.OperationType.names);
    }

    /**
     * This test checks that the list of ResourceType defined in flatbuffers matches the list defined by the current
     * Keycloak version. If it is not the case, it means that the file events.fbs needs to be updated according
     * to the new ResourceType file defined in the root GitHub repository of the correct version.
     */
    @Test
    void testResourceTypesAreUpToDate() {
        validateNames(ResourceType.class, flatbuffers.events.ResourceType.names);
    }
}
