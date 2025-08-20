package io.cloudtrust.keycloak.eventemitter.httpemitter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.stefanbirkner.systemlambda.Statement;
import com.github.stefanbirkner.systemlambda.SystemLambda;
import io.cloudtrust.keycloak.eventemitter.Container;
import io.cloudtrust.keycloak.eventemitter.customevent.ExtendedAdminEvent;
import io.cloudtrust.keycloak.eventemitter.customevent.IdentifiedEvent;
import io.cloudtrust.keycloak.eventemitter.snowflake.IdGenerator;
import io.cloudtrust.keycloak.test.http.HttpServerManager;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.StatusCodes;
import org.apache.commons.io.IOUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.SignerInformationVerifier;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.crypto.Algorithm;
import org.keycloak.crypto.KeyUse;
import org.keycloak.crypto.KeyWrapper;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.AuthDetails;
import org.keycloak.events.admin.OperationType;
import org.keycloak.models.KeyManager;
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
import org.mockito.MockitoAnnotations;
import org.xnio.streams.ChannelInputStream;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.LinkedBlockingQueue;

import static org.mockito.Mockito.mock;

class HttpEventEmitterProviderTest {
    private static final int LISTEN_PORT = 9994;
    private static final String TARGET = "http://localhost:" + LISTEN_PORT + "/test";
    private static final int BUFFER_CAPACITY = 3;
    private static final String IDP_ID = "test-idp";

    private static final String username = "toto";
    private static final String password = "passwordverylongandhardtoguess";
    private static final String userId = "394b0730-628f-11ec-9211-0242ac120005";

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private KeycloakSession keycloakSession;

    protected void runWithEnvironments(Statement stmt) throws Exception {
        SystemLambda.withEnvironmentVariable(HttpEventEmitterProvider.KEYCLOAK_BRIDGE_SECRET_TOKEN, password)
                .and(HttpEventEmitterProvider.HOSTNAME, username)
                .execute(stmt);
    }

    protected void runWithHttpHandler(HttpHandler handler, Statement stmt) {
        try {
            HttpServerManager.getDefault().startHttpServer(LISTEN_PORT, handler);
            this.runWithEnvironments(stmt);
        } catch (Exception e) {
            Assertions.fail(e);
        } finally {
            HttpServerManager.getDefault().stop(LISTEN_PORT);
        }
    }

    @BeforeEach
    public void initMock() throws NoSuchAlgorithmException, CertIOException, CertificateException, OperatorCreationException {
        MockitoAnnotations.openMocks(this);
        // user
        UserEntity userEntity = new UserEntity();
        userEntity.setUsername("test-user");
        UserModel user = new UserAdapter(null, null, null, userEntity);
        Mockito.when(keycloakSession.users().getUserById(Mockito.any(), Mockito.any())).thenReturn(user);

        // Realm
        RealmEntity realmEntity = new RealmEntity();
        realmEntity.setId("realmId");
        RealmModel realm = new RealmAdapter(null, null, realmEntity);
        Mockito.when(keycloakSession.realms().getRealm(Mockito.any())).thenReturn(realm);

        // Signature keys
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        KeyWrapper keyWrapper = mock(KeyWrapper.class);
        Mockito.when(keyWrapper.getPrivateKey()).thenReturn(keyPair.getPrivate());
        Mockito.when(keyWrapper.getPublicKey()).thenReturn(keyPair.getPublic());

        // Dummy self-signed certificate
        Security.addProvider(new BouncyCastleProvider());

        X500Name x500Name = new X500Name("CN=Test Realm");
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        Date notBefore = new Date(System.currentTimeMillis());
        Date notAfter = new Date(System.currentTimeMillis());

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(x500Name, serial, notBefore, notAfter, x500Name, keyPair.getPublic());
        certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        certBuilder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyCertSign));

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA").setProvider("BC").build(keyPair.getPrivate());
        X509Certificate cert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(certBuilder.build(signer));

        Mockito.when(keyWrapper.getCertificate()).thenReturn(cert);

        KeyManager keyManager = mock(KeyManager.class);
        Mockito.when(keyManager.getActiveKey(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(keyWrapper);
        Mockito.when(keycloakSession.keys()).thenReturn(keyManager);
    }

    @Test
    void testFlatbufferFormatOutput() {
        final HttpJsonReceiverHandler handler = new HttpJsonReceiverHandler();
        runWithHttpHandler(handler, () -> {
            CloseableHttpClient httpClient = HttpClients.createDefault();
            IdGenerator idGenerator = new IdGenerator(1, 1);
            LinkedBlockingQueue<IdentifiedEvent> pendingEvents = new LinkedBlockingQueue<>(BUFFER_CAPACITY);
            LinkedBlockingQueue<ExtendedAdminEvent> pendingAdminEvents = new LinkedBlockingQueue<>(BUFFER_CAPACITY);
            HttpEventEmitterProvider HttpEventEmitterProvider = new HttpEventEmitterProvider(keycloakSession, httpClient,
                    idGenerator, TARGET, pendingEvents, pendingAdminEvents, null, IDP_ID);

            Event event = createEvent();
            HttpEventEmitterProvider.onEvent(event);

            httpClient.close();

            ObjectMapper mapper = new ObjectMapper();
            Container container = mapper.readValue(handler.getResponse(), Container.class);

            byte[] b = Base64.getDecoder().decode(container.obj());
            flatbuffers.events.Event receivedEvent = flatbuffers.events.Event.getRootAsEvent(ByteBuffer.wrap(b));
            Assertions.assertEquals(event.getTime(), receivedEvent.time());
            Assertions.assertEquals(event.getType().ordinal(), receivedEvent.type());
            Assertions.assertEquals(event.getClientId(), receivedEvent.clientId());
            Assertions.assertEquals(handler.getHeader("IDP-ID"), IDP_ID);
        });
    }

    @Test
    void testAdminEventFlatbufferFormatOutput() {
        final HttpJsonReceiverHandler handler = new HttpJsonReceiverHandler();
        runWithHttpHandler(handler, () -> {
            CloseableHttpClient httpClient = HttpClients.createDefault();
            IdGenerator idGenerator = new IdGenerator(1, 1);
            LinkedBlockingQueue<IdentifiedEvent> pendingEvents = new LinkedBlockingQueue<>(BUFFER_CAPACITY);
            LinkedBlockingQueue<ExtendedAdminEvent> pendingAdminEvents = new LinkedBlockingQueue<>(BUFFER_CAPACITY);
            HttpEventEmitterProvider HttpEventEmitterProvider = new HttpEventEmitterProvider(keycloakSession, httpClient,
                    idGenerator, TARGET, pendingEvents, pendingAdminEvents, null, IDP_ID);

            AdminEvent event = createAdminEvent();
            HttpEventEmitterProvider.onEvent(event, true);

            httpClient.close();

            ObjectMapper mapper = new ObjectMapper();
            Container container = mapper.readValue(handler.getResponse(), Container.class);

            byte[] b = Base64.getDecoder().decode(container.obj());
            flatbuffers.events.AdminEvent receivedEvent = flatbuffers.events.AdminEvent.getRootAsAdminEvent(ByteBuffer.wrap(b));
            Assertions.assertEquals(event.getTime(), receivedEvent.time());
            Assertions.assertEquals(event.getOperationType().ordinal(), receivedEvent.operationType());
            Assertions.assertEquals(event.getAuthDetails().getUserId(), receivedEvent.authDetails().userId());
            Assertions.assertEquals(handler.getHeader("IDP-ID"), IDP_ID);
        });
    }

    @Test
    void testNoConnection() throws Exception {
        runWithEnvironments(() -> {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                IdGenerator idGenerator = new IdGenerator(1, 1);
                LinkedBlockingQueue<IdentifiedEvent> pendingEvents = new LinkedBlockingQueue<>(BUFFER_CAPACITY);
                LinkedBlockingQueue<ExtendedAdminEvent> pendingAdminEvents = new LinkedBlockingQueue<>(BUFFER_CAPACITY);
                HttpEventEmitterProvider HttpEventEmitterProvider = new HttpEventEmitterProvider(keycloakSession, httpClient,
                        idGenerator, TARGET, pendingEvents, pendingAdminEvents, null, IDP_ID);

                Assertions.assertEquals(0, pendingEvents.size());

                Event event = createEvent();
                HttpEventEmitterProvider.onEvent(event);

                Assertions.assertEquals(1, pendingEvents.size());

                Event event2 = createEvent();
                HttpEventEmitterProvider.onEvent(event2);

                Assertions.assertEquals(2, pendingEvents.size());
            }
        });
    }

    @Test
    void testServerError() {
        runWithHttpHandler(
                ex -> ex.setStatusCode(StatusCodes.MULTIPLE_CHOICES),
                () -> {
                    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                        IdGenerator idGenerator = new IdGenerator(1, 1);
                        LinkedBlockingQueue<IdentifiedEvent> pendingEvents = new LinkedBlockingQueue<>(BUFFER_CAPACITY);
                        LinkedBlockingQueue<ExtendedAdminEvent> pendingAdminEvents = new LinkedBlockingQueue<>(BUFFER_CAPACITY);
                        HttpEventEmitterProvider HttpEventEmitterProvider = new HttpEventEmitterProvider(keycloakSession, httpClient,
                                idGenerator, TARGET, pendingEvents, pendingAdminEvents, null, IDP_ID);

                        Assertions.assertEquals(0, pendingEvents.size());

                        Event event = createEvent();
                        HttpEventEmitterProvider.onEvent(event);

                        Assertions.assertEquals(1, pendingEvents.size());

                        Event event2 = createEvent();
                        HttpEventEmitterProvider.onEvent(event2);

                        Assertions.assertEquals(2, pendingEvents.size());
                    }
                });
    }

    @Test
    void testBufferAndSend() throws Exception {
        runWithEnvironments(() -> {
            CloseableHttpClient httpClient = HttpClients.createDefault();
            IdGenerator idGenerator = new IdGenerator(1, 1);
            LinkedBlockingQueue<IdentifiedEvent> pendingEvents = new LinkedBlockingQueue<>(BUFFER_CAPACITY);
            LinkedBlockingQueue<ExtendedAdminEvent> pendingAdminEvents = new LinkedBlockingQueue<>(BUFFER_CAPACITY);
            HttpEventEmitterProvider HttpEventEmitterProvider = new HttpEventEmitterProvider(keycloakSession, httpClient,
                    idGenerator, TARGET, pendingEvents, pendingAdminEvents, null, IDP_ID);

            Assertions.assertEquals(0, pendingEvents.size());

            Event event = createEvent();
            HttpEventEmitterProvider.onEvent(event);

            Assertions.assertEquals(1, pendingEvents.size());

            Event event2 = createEvent();
            HttpEventEmitterProvider.onEvent(event2);

            Assertions.assertEquals(2, pendingEvents.size());

            try {
                HttpJsonReceiverHandler handler = new HttpJsonReceiverHandler();
                HttpServerManager.getDefault().startHttpServer(LISTEN_PORT, handler);

                Event event3 = createEvent();
                HttpEventEmitterProvider.onEvent(event3);

                httpClient.close();

                Assertions.assertEquals(0, pendingEvents.size());
                Assertions.assertEquals(3, handler.getCounter());
            } finally {
                HttpServerManager.getDefault().stop(LISTEN_PORT);
            }
        });
    }

    @Test
    void testSignature() {
        final HttpJsonReceiverHandler handler = new HttpJsonReceiverHandler();
        runWithHttpHandler(handler, () -> {
            CloseableHttpClient httpClient = HttpClients.createDefault();
            IdGenerator idGenerator = new IdGenerator(1, 1);
            LinkedBlockingQueue<IdentifiedEvent> pendingEvents = new LinkedBlockingQueue<>(BUFFER_CAPACITY);
            LinkedBlockingQueue<ExtendedAdminEvent> pendingAdminEvents = new LinkedBlockingQueue<>(BUFFER_CAPACITY);
            HttpEventEmitterProvider HttpEventEmitterProvider = new HttpEventEmitterProvider(keycloakSession, httpClient,
                    idGenerator, TARGET, pendingEvents, pendingAdminEvents, null, IDP_ID);

            AdminEvent event = createAdminEvent();
            HttpEventEmitterProvider.onEvent(event, true);

            httpClient.close();

            // Verify signature with realm public key
            KeyWrapper keyWrapper = keycloakSession.keys().getActiveKey(keycloakSession.getContext().getRealm(),
                    KeyUse.SIG, Algorithm.RS256);
            PublicKey publicKey = (PublicKey) keyWrapper.getPublicKey();

            Security.addProvider(new BouncyCastleProvider());
            byte[] signature = Base64.getDecoder().decode(handler.getHeader("PKCS7-Signature"));
            CMSSignedData signedData;
            byte[] payload = handler.getResponse().getBytes();
            CMSTypedData payloadData = new CMSProcessableByteArray(payload);
            signedData = new CMSSignedData(payloadData, signature);

            SignerInformationStore signers = signedData.getSignerInfos();

            for (SignerInformation signerInfo : signers.getSigners()) {
                SignerInformationVerifier verifier = new JcaSimpleSignerInfoVerifierBuilder()
                        .setProvider("BC")
                        .build(publicKey);

                Assertions.assertTrue(signerInfo.verify(verifier));
            }
        });
    }

    static class HttpJsonReceiverHandler implements HttpHandler {
        HeaderMap headers;
        String jsonReceived;
        int counter = 0;

        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            exchange.setResponseCode(StatusCodes.OK);
            ChannelInputStream cis = new ChannelInputStream(exchange.getRequestChannel());
            headers = exchange.getRequestHeaders();
            jsonReceived = IOUtils.toString(cis, StandardCharsets.UTF_8);
            counter++;
        }

        String getResponse() {
            return jsonReceived;
        }

        String getHeader(String headerName) {
            return headers.get(headerName).getFirst();
        }

        int getCounter() {
            return counter;
        }
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
