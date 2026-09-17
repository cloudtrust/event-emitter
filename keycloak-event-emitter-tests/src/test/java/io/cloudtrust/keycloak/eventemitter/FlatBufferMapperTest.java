package io.cloudtrust.keycloak.eventemitter;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FlatBufferMapperTest {
    @Test
    void testSuccessFlatBufferMapperWithoutUnknown() {
        String[] flatBufferEnumNames = {"VALUE1", "VALUE2", "VALUE3"};
        var mapper = new FlatBufferMapper<>(MyEnum.class, flatBufferEnumNames, false);

        Assertions.assertEquals(0, mapper.get(MyEnum.VALUE1));
        Assertions.assertEquals(1, mapper.get(MyEnum.VALUE2));
        Assertions.assertEquals(2, mapper.get(MyEnum.VALUE3));
        Assertions.assertEquals(0, mapper.get(null)); // default value for null
    }

    @Test
    void testSuccessFlatBufferMapperWithUnknown() {
        String[] flatBufferEnumNames = {"VALUE1", "VALUE2", "VALUE3", "UNKNOWN"};
        var mapper = new FlatBufferMapper<>(MyEnum.class, flatBufferEnumNames);

        Assertions.assertEquals(0, mapper.get(MyEnum.VALUE1));
        Assertions.assertEquals(1, mapper.get(MyEnum.VALUE2));
        Assertions.assertEquals(2, mapper.get(MyEnum.VALUE3));
        Assertions.assertEquals(0, mapper.get(null)); // default value for null
    }

    @Test
    void testFailureBadCount() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            String[] flatBufferEnumNames = {"VALUE1", "VALUE2"};
            new FlatBufferMapper<>(MyEnum.class, flatBufferEnumNames);
        });
    }

    @Test
    void testFailureMissingUnknown() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            String[] flatBufferEnumNames = {"VALUE1", "VALUE2", "VALUE3"};
            new FlatBufferMapper<>(MyEnum.class, flatBufferEnumNames);
        });
    }

    @Test
    void testFailureMissingMatchingName() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            String[] flatBufferEnumNames = {"VALUE1", "VALUE2", "VALUE4", "UNKNOWN"};
            new FlatBufferMapper<>(MyEnum.class, flatBufferEnumNames);
        });
    }

    public enum MyEnum {
        VALUE1, VALUE2, VALUE3
    }
}
