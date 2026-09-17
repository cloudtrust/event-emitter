package io.cloudtrust.keycloak.eventemitter;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class FlatBufferMapper<K extends Enum<K>> {
    private final Map<K, Integer> map;
    private final Integer defaultValue;

    public FlatBufferMapper(Class<K> keycloakEnumClass, String[] flatBufferEnumNames) {
        this(keycloakEnumClass, flatBufferEnumNames, true);
    }

    public FlatBufferMapper(Class<K> keycloakEnumClass, String[] flatBufferEnumNames, boolean hasUnknown) {
        K[] keyValues = keycloakEnumClass.getEnumConstants();

        var valuesByName = new HashMap<String, Integer>();
        for (int i = 0; i < flatBufferEnumNames.length; i++) {
            valuesByName.put(flatBufferEnumNames[i], i);
        }
        defaultValue = evaluateUnknown(keycloakEnumClass, valuesByName, hasUnknown);

        var unknownCount = hasUnknown ? 1 : 0;
        if (keyValues.length != flatBufferEnumNames.length-unknownCount) {
            // -1 because the flatbuffer enum has an extra value for UNKNOWN
            throw new IllegalArgumentException(String.format(
                    "Enum cardinalities do not match between %s (%d) and its matching flatbuffer type (%d)",
                    keycloakEnumClass.getName(), keyValues.length, flatBufferEnumNames.length-unknownCount));
        }

        this.map = new EnumMap<>(keycloakEnumClass);
        for (K key : keyValues) {
            Integer value = valuesByName.get(key.name());
            if (value == null) {
                throw new IllegalArgumentException(String.format(
                        "No matching enum value named %s from %s in its matching flatbuffer type",
                        key.name(), keycloakEnumClass.getName()));
            }
            this.map.put(key, value);
        }
    }

    private Integer evaluateUnknown(Class<K> keycloakEnumClass, Map<String, Integer> valuesByName, boolean hasUnknown) {
        if (!hasUnknown) {
            return 0;
        }
        var unknown = valuesByName.get("UNKNOWN");
        if (unknown==null) {
            throw new IllegalArgumentException(
                    "No matching enum value named UNKNOWN in flatbuffer type matching "
                            + keycloakEnumClass.getName());
        }
        return unknown;
    }

    public int get(K key) {
        if (key == null) {
            return 0;
        }
        var res = map.get(key);
        return res==null ? defaultValue : res;
    }
}
