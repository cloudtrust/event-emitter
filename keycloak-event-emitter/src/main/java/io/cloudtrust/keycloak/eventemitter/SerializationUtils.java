package io.cloudtrust.keycloak.eventemitter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.flatbuffers.FlatBufferBuilder;
import io.cloudtrust.keycloak.eventemitter.customevent.ExtendedAdminEvent;
import io.cloudtrust.keycloak.eventemitter.customevent.IdentifiedEvent;

import java.nio.ByteBuffer;
import java.util.Map;

/**
 * Utility class to serialize Event and AdminEvent in Flatbuffer or JSON format.
 * In order to allow idempotence, a unique ID is added to the entity.
 */
public class SerializationUtils {
    private static final int FLATBUFFER_INIT_SIZE = 1024;

    private SerializationUtils() {
    }

    public static String toJson(Object obj) throws JsonProcessingException {
        return new ObjectMapper().writeValueAsString(obj);
    }

    public static ByteBuffer toFlat(IdentifiedEvent event) {
        FlatBufferBuilder builder = new FlatBufferBuilder(FLATBUFFER_INIT_SIZE);

        // uid
        long uid = event.getUid();

        // Time
        long time = event.getTime();

        // Type
        byte type;
        // size of the list minus 1 because we add UNKNOWN event type in flatbuffers list
        int eventTypeSize = flatbuffers.events.EventType.names.length - 1;

        if (event.getType().ordinal() < eventTypeSize) {
            type = (byte) event.getType().ordinal();
        } else {
            // EventType returned by the Event is unknown by flatbuffers
            type = flatbuffers.events.EventType.UNKNOWN;
        }

        int realmId = createString(builder, event.getRealmId());
        int clientId = createString(builder, event.getClientId());
        int userId = createString(builder, event.getUserId());
        int sessionId = createString(builder, event.getSessionId());
        int ipAddress = createString(builder, event.getIpAddress());
        int error = createString(builder, event.getError());
        int detailsVec = createMap(builder, event.getDetails());

        flatbuffers.events.Event.startEvent(builder);

        flatbuffers.events.Event.addUid(builder, uid);
        flatbuffers.events.Event.addTime(builder, time);
        flatbuffers.events.Event.addType(builder, type);
        flatbuffers.events.Event.addRealmId(builder, realmId);
        flatbuffers.events.Event.addClientId(builder, clientId);
        flatbuffers.events.Event.addUserId(builder, userId);
        flatbuffers.events.Event.addSessionId(builder, sessionId);
        flatbuffers.events.Event.addIpAddress(builder, ipAddress);
        flatbuffers.events.Event.addError(builder, error);
        flatbuffers.events.Event.addDetails(builder, detailsVec);

        int flatEvent = flatbuffers.events.Event.endEvent(builder);

        builder.finish(flatEvent);

        return builder.dataBuffer();
    }

    public static ByteBuffer toFlat(ExtendedAdminEvent adminEvent) {
        FlatBufferBuilder builder = new FlatBufferBuilder(FLATBUFFER_INIT_SIZE);

        // uid
        long uid = adminEvent.getUid();

        // Time
        long timeOffset = adminEvent.getTime();

        // RealmId
        int realmIdOffset = 0;
        if (adminEvent.getRealmId() != null) {
            realmIdOffset = builder.createString(adminEvent.getRealmId());
        }

        // AuthDetails
        int authDetailsOffset = 0;
        if (adminEvent.getAuthDetails() != null) {
            int authDetailsRealmIdOffset = createString(builder, adminEvent.getAuthDetails().getRealmId());
            int authDetailsClientIdOffset = createString(builder, adminEvent.getAuthDetails().getClientId());
            int authDetailsUserIdOffset = createString(builder, adminEvent.getAuthDetails().getUserId());
            int authDetailsUsernameAddressOffset = createString(builder, adminEvent.getAuthDetails().getUsername());
            int authDetailsIpAddressOffset = createString(builder, adminEvent.getAuthDetails().getIpAddress());

            authDetailsOffset = flatbuffers.events.AuthDetails.createAuthDetails(builder,
                    authDetailsRealmIdOffset, authDetailsClientIdOffset,
                    authDetailsUserIdOffset, authDetailsUsernameAddressOffset,
                    authDetailsIpAddressOffset);
        }

        // ResourceType
        byte resourceTypeOffset = 0;
        // size of the list minus 1 because we add UNKNOWN resource type in flatbuffers list
        int resourceTypeSize = flatbuffers.events.ResourceType.names.length - 1;

        if (adminEvent.getResourceType() != null) {
            if (adminEvent.getResourceType().ordinal() < resourceTypeSize) {
                resourceTypeOffset = (byte) adminEvent.getResourceType().ordinal();
            } else {
                // ResourceType returned by the AdminEvent is unknown by flatbuffers
                resourceTypeOffset = flatbuffers.events.ResourceType.UNKNOWN;
            }
        }

        // OperationType
        byte operationTypeOffset = 0;
        if (adminEvent.getOperationType() != null) {
            operationTypeOffset = (byte) adminEvent.getOperationType().ordinal();
        }

        // ResourcePath
        int resourcePathOffset = 0;
        if (adminEvent.getResourcePath() != null) {
            resourcePathOffset = builder.createString(adminEvent.getResourcePath());
        }

        // Representation
        int representationOffset = 0;
        if (adminEvent.getRepresentation() != null) {
            representationOffset = builder.createString(adminEvent.getRepresentation());
        }

        // Details
        int detailsVec = createMap(builder, adminEvent.getDetails());

        // Error
        int errorOffset = 0;
        if (adminEvent.getError() != null) {
            errorOffset = builder.createString(adminEvent.getError());
        }

        flatbuffers.events.AdminEvent.startAdminEvent(builder);

        flatbuffers.events.AdminEvent.addUid(builder, uid);
        flatbuffers.events.AdminEvent.addTime(builder, timeOffset);
        flatbuffers.events.AdminEvent.addRealmId(builder, realmIdOffset);
        flatbuffers.events.AdminEvent.addAuthDetails(builder, authDetailsOffset);
        flatbuffers.events.AdminEvent.addResourceType(builder, resourceTypeOffset);
        flatbuffers.events.AdminEvent.addOperationType(builder, operationTypeOffset);
        flatbuffers.events.AdminEvent.addResourcePath(builder, resourcePathOffset);
        flatbuffers.events.AdminEvent.addRepresentation(builder, representationOffset);
        flatbuffers.events.AdminEvent.addDetails(builder, detailsVec);
        flatbuffers.events.AdminEvent.addError(builder, errorOffset);

        int flatAdminEvent = flatbuffers.events.AdminEvent.endAdminEvent(builder);

        builder.finish(flatAdminEvent);

        return builder.dataBuffer();
    }

    private static int createString(FlatBufferBuilder builder, String value) {
        return value != null ? builder.createString(value) : 0;
    }

    private static int createMap(FlatBufferBuilder builder, Map<String, String> map) {
        if (map == null) {
            return 0;
        }
        int[] details = map.entrySet().stream()
                .mapToInt(entry -> {
                    int key = builder.createString(entry.getKey());
                    int value = builder.createString(entry.getValue());
                    return flatbuffers.events.Tuple.createTuple(builder, key, value);
                })
                .toArray();
        return flatbuffers.events.Event.createDetailsVector(builder, details);
    }
}
