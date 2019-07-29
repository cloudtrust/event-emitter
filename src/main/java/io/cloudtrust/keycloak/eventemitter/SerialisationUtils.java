package io.cloudtrust.keycloak.eventemitter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.flatbuffers.FlatBufferBuilder;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * Utility class to serialize Event and AdminEvent in Flatbuffer or JSON format.
 * In order to allow idempotence, a unique ID is added to the entity.
 */
class SerialisationUtils {
    private static final ThreadLocal<ObjectMapper> objectMapperProvider = ThreadLocal.withInitial(ObjectMapper::new);

    private static final int FLATBUFFER_INIT_SIZE = 1024;

    static String toJson(Object obj) throws JsonProcessingException {
        return objectMapperProvider.get().writeValueAsString(obj);
    }

    static ByteBuffer toFlat(IdentifiedEvent event){
        FlatBufferBuilder builder = new FlatBufferBuilder(FLATBUFFER_INIT_SIZE);

        // uid
        long uid = event.getUid();

        // Time
        long time = event.getTime();

        // Type
        byte type;
        // size of the list minus 1 because we add UNKNOWN event type in flatbuffers list
        int eventTypeSize = flatbuffers.events.EventType.names.length -1;

        if (event.getType().ordinal() < eventTypeSize){
            type = (byte) event.getType().ordinal();
        }else{
            // EventType returned by the Event is unknown by flatbuffers
            type = flatbuffers.events.EventType.UNKNOWN;
        }

        // RealmId
        int realmId = 0;
        if (event.getRealmId() != null ){
            realmId = builder.createString(event.getRealmId());
        }

        // ClientId
        int clientId = 0;
        if (event.getClientId() != null ) {
            clientId = builder.createString(event.getClientId());
        }

        // UserId
        int userId = 0;
        if (event.getUserId() != null ) {
            userId = builder.createString(event.getUserId());
        }

        // SessionId
        int sessionId = 0;
        if (event.getSessionId() != null ) {
            sessionId = builder.createString(event.getSessionId());
        }

        // IpAddress
        int ipAddress = 0;
        if (event.getIpAddress() != null ) {
            ipAddress = builder.createString(event.getIpAddress());
        }

        // Error
        int error = 0;
        if (event.getError() != null ) {
            error = builder.createString(event.getError());
        }

        // Details
        int detailsVec = 0;
        if (event.getDetails() != null ) {
            List<Integer> tuples = new ArrayList<>();
            for (Map.Entry<String, String> entry : event.getDetails().entrySet()) {
                int key = builder.createString(entry.getKey());
                int value = builder.createString(entry.getValue());
                int tuple = flatbuffers.events.Tuple.createTuple(builder, key, value);
                tuples.add(tuple);
            }

            int[] details = new int[tuples.size()];
            for (int i = 0; i < tuples.size(); i++) {
                details[i] = tuples.get(i);
            }

            detailsVec = flatbuffers.events.Event.createDetailsVector(builder, details);
        }

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

    static ByteBuffer toFlat(IdentifiedAdminEvent adminEvent){
        FlatBufferBuilder builder = new FlatBufferBuilder(FLATBUFFER_INIT_SIZE);

        // uid
        long uid = adminEvent.getUid();

        // Time
        long timeOffset = adminEvent.getTime();

        // RealmId
        int realmIdOffset = 0;
        if (adminEvent.getRealmId() != null ){
            realmIdOffset = builder.createString(adminEvent.getRealmId());
        }

        // AuthDetails
        int authDetailsOffset = 0;
        if (adminEvent.getAuthDetails() != null){
            int authDetailsRealmIdOffset = 0;
            int authDetailsClientIdOffset = 0;
            int authDetailsUserIdOffset = 0;
            int authDetailsIpAddressOffset = 0;

            if (adminEvent.getAuthDetails().getRealmId() != null){
                authDetailsRealmIdOffset = builder.createString(adminEvent.getAuthDetails().getRealmId());
            }

            if (adminEvent.getAuthDetails().getClientId() != null) {
                authDetailsClientIdOffset = builder.createString(adminEvent.getAuthDetails().getClientId());
            }

            if (adminEvent.getAuthDetails().getUserId() != null) {
                authDetailsUserIdOffset = builder.createString(adminEvent.getAuthDetails().getUserId());
            }

            if (adminEvent.getAuthDetails().getIpAddress() != null) {
                authDetailsIpAddressOffset = builder.createString(adminEvent.getAuthDetails().getIpAddress());
            }

            authDetailsOffset = flatbuffers.events.AuthDetails.createAuthDetails(builder,
                    authDetailsRealmIdOffset, authDetailsClientIdOffset,
                    authDetailsUserIdOffset, authDetailsIpAddressOffset);
        }

        // ResourceType
        byte resourceTypeOffset = 0;
        // size of the list minus 1 because we add UNKNOWN resource type in flatbuffers list
        int resourceTypeSize = flatbuffers.events.ResourceType.names.length -1;

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
        if(adminEvent.getOperationType() != null){
            operationTypeOffset = (byte) adminEvent.getOperationType().ordinal();
        }

        // ResourcePath
        int resourcePathOffset = 0;
        if (adminEvent.getResourcePath() != null ) {
            resourcePathOffset = builder.createString(adminEvent.getResourcePath());
        }

        // Representation
        int representationOffset = 0;
        if (adminEvent.getRepresentation() != null ) {
            representationOffset = builder.createString(adminEvent.getRepresentation());
        }

        // Error
        int errorOffset = 0;
        if (adminEvent.getError() != null ) {
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
        flatbuffers.events.AdminEvent.addError(builder, errorOffset);

        int flatAdminEvent = flatbuffers.events.AdminEvent.endAdminEvent(builder);

        builder.finish(flatAdminEvent);

        return builder.dataBuffer();
    }

}
