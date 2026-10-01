package flatbuffers.events;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;

import java.util.Arrays;
import java.util.stream.Collectors;

public class TypesTest {
    protected <E extends Enum<E>> void validateNames(Class<E> enumClass, String[] flatbufferTypes) {
        var enumNames = Arrays.stream(enumClass.getEnumConstants()).map(Enum::name).collect(Collectors.toSet());
        var flatbufferTypeList = Arrays.stream(flatbufferTypes).filter(t -> !"UNKNOWN".equals(t)).toList();
        Assertions.assertEquals(enumNames.size(), flatbufferTypeList.size(), "The number of types in the enum " + enumClass.getName() + " does not match the number of types in the flatbuffer definition");
        flatbufferTypeList.forEach(t -> Assertions.assertTrue(enumNames.contains(t), "The type " + t + " is defined in the flatbuffer definition but not in the enum " + enumClass.getName()));
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
