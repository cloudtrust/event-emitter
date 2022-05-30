package io.cloudtrust.keycloak.kafkaeventemitter;


import io.cloudtrust.keycloak.snowflake.IdGenerator;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.AuthDetails;
import org.keycloak.events.admin.OperationType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.jpa.RealmAdapter;
import org.keycloak.models.jpa.UserAdapter;
import org.keycloak.models.jpa.entities.RealmEntity;
import org.keycloak.models.jpa.entities.UserEntity;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

@RunWith(MockitoJUnitRunner.class)
public class KafkaEventEmitterProviderTest {
    private static final String topicEvent = "test-event";
    private static final String topicAdminEvent = "test-admin-event";
    private static final String userId = "394b0730-628f-11ec-9211-0242ac120005";

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private KeycloakSession keycloakSession;
    private MockProducer<String, String> mockProducer;

    @Before
    public void initMock() {
        // user
        UserEntity userEntity = new UserEntity();
        userEntity.setUsername("test-user");
        UserModel user = new UserAdapter(null, null, null, userEntity);
        Mockito.when(keycloakSession.users().getUserById((RealmModel) Mockito.any(), Mockito.any())).thenReturn(user);

        // Realm
        RealmEntity realmEntity = new RealmEntity();
        realmEntity.setId("realmId");
        RealmModel realm = new RealmAdapter(null, null, realmEntity);
        Mockito.when(keycloakSession.realms().getRealm(Mockito.any())).thenReturn(realm);

        mockProducer = new MockProducer<>(true, new StringSerializer(), new StringSerializer());
    }

    @Test
    public void testEventFlatbufferFormatOutput() {
        IdGenerator idGenerator = new IdGenerator(1, 1);
        LinkedBlockingQueue<ProducerRecord<String, String>> pendingEvents = new LinkedBlockingQueue<>(50);

        KafkaEventEmitterState state = new KafkaEventEmitterState();
        state.working();
        KafkaEventEmitterProvider kafkaEventEmitterProvider = new KafkaEventEmitterProvider(keycloakSession, mockProducer, topicEvent, topicAdminEvent, idGenerator, pendingEvents, state, new ReentrantLock());

        Event event = createEvent();
        kafkaEventEmitterProvider.onEvent(event);
        List<ProducerRecord<String, String>> recordList = mockProducer.history();

        ProducerRecord<String, String> producedEvent = recordList.get(0);

        byte[] b = Base64.getDecoder().decode(producedEvent.value());
        flatbuffers.events.Event receivedEvent = flatbuffers.events.Event.getRootAsEvent(ByteBuffer.wrap(b));
        Assert.assertEquals(event.getTime(), receivedEvent.time());
        Assert.assertEquals(event.getType().ordinal(), receivedEvent.type());
        Assert.assertEquals(event.getClientId(), receivedEvent.clientId());
    }

    @Test
    public void testAdminEventFlatbufferFormatOutput() {
        IdGenerator idGenerator = new IdGenerator(1, 1);
        LinkedBlockingQueue<ProducerRecord<String, String>> pendingEvents = new LinkedBlockingQueue<>(50);

        KafkaEventEmitterState state = new KafkaEventEmitterState();
        state.working();
        KafkaEventEmitterProvider kafkaEventEmitterProvider = new KafkaEventEmitterProvider(keycloakSession, mockProducer, topicEvent, topicAdminEvent, idGenerator, pendingEvents, state, new ReentrantLock());

        AdminEvent event = createAdminEvent();
        kafkaEventEmitterProvider.onEvent(event, false);
        List<ProducerRecord<String, String>> recordList = mockProducer.history();

        ProducerRecord<String, String> producedEvent = recordList.get(0);

        byte[] b = Base64.getDecoder().decode(producedEvent.value());

        flatbuffers.events.AdminEvent receivedEvent = flatbuffers.events.AdminEvent.getRootAsAdminEvent(ByteBuffer.wrap(b));
        Assert.assertEquals(event.getTime(), receivedEvent.time());
        Assert.assertEquals(event.getOperationType().ordinal(), receivedEvent.operationType());
        Assert.assertEquals(event.getAuthDetails().getUserId(), receivedEvent.authDetails().userId());
    }

    @Test
    public void testNoConnection() {
        IdGenerator idGenerator = new IdGenerator(1, 1);
        MockProducer<String, String> stepMockProducer = new MockProducer<>(false, new StringSerializer(), new StringSerializer());
        LinkedBlockingQueue<ProducerRecord<String, String>> pendingEvents = new LinkedBlockingQueue<>(50);

        KafkaEventEmitterState state = new KafkaEventEmitterState();
        state.working();
        KafkaEventEmitterProvider kafkaEventEmitterProvider = new KafkaEventEmitterProvider(keycloakSession, mockProducer, topicEvent, topicAdminEvent, idGenerator, pendingEvents, state, new ReentrantLock());

        stepMockProducer.errorNext(new RuntimeException("Test error"));
        Event event = createEvent();
        kafkaEventEmitterProvider.onEvent(event);
        List<ProducerRecord<String, String>> recordList = mockProducer.history();
        Assert.assertEquals(1, recordList.size());
    }

    private Event createEvent() {
        Event event = new Event();
        event.setTime(120001);
        event.setType(EventType.CLIENT_LOGIN);
        return event;
    }

    private AdminEvent createAdminEvent() {
        AdminEvent event = new AdminEvent();
        event.setTime(120001);
        event.setOperationType(OperationType.CREATE);
        AuthDetails authDetails = new AuthDetails();
        authDetails.setUserId(userId);
        event.setAuthDetails(authDetails);
        return event;
    }
}
