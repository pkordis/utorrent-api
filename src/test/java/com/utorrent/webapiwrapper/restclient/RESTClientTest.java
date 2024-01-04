package com.utorrent.webapiwrapper.restclient;

import com.utorrent.webapiwrapper.restclient.exceptions.*;
import org.apache.http.*;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicStatusLine;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.*;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith({
    MockitoExtension.class
})
class RESTClientTest {
    @Mock
    private CloseableHttpClient httpClient;

    private final ConnectionParams connectionParams = ConnectionParams.builder()
            .withScheme("http")
            .withCredentials("username", "password")
            .withAddress("host.com", 8080)
            .withTimeout(1500)
            .create();


    private RESTClient client;

    @BeforeEach
    void setUp() throws URISyntaxException {
        client = new RESTClient(httpClient, connectionParams);
    }


    @Test
    void whenRequestIsNullThenPostThrowException() throws Exception {
        assertThrows(NullPointerException.class, () -> client.post(null));
    }

    @Test
    void whenRequestIsNullThenGetThrowException() throws Exception {
        assertThrows(NullPointerException.class, () -> client.get(null));
    }

    @Test
    void testPost() throws Exception {

    }

    @Test
    void testGet() throws Exception {
    }

    @Test
    void testNotAcceptableException() throws IOException {
        testException(406, NotAcceptableException.class);
    }

    @Test
    void testNotFoundException() throws IOException {
        testException(404, NotFoundException.class);
    }

    @Test
    void testForbiddenException() throws IOException {
        testException(403, ForbiddenException.class);
    }

    @Test
    void testUnauthorizedException() throws IOException {
        testException(401, UnauthorizedException.class);
    }

    @Test
    void testClientRequestException() throws IOException {
        testException(300, ClientRequestException.class);
        testException(450, ClientRequestException.class);
        testException(500, ClientRequestException.class);
    }

    @Test
    void testBadRequestException() throws IOException {
        testException(400, BadRequestException.class);
    }

    private <T extends Exception> void testException(int code, Class<T> exception) throws IOException {
        CloseableHttpResponse httpResponse = mock(CloseableHttpResponse.class);
        HttpEntity entity = mock(HttpEntity.class);
        when(httpClient.execute(any(HttpUriRequest.class), any(HttpClientContext.class)))
                .thenReturn(httpResponse);
        when(httpResponse.getEntity()).thenReturn(entity);
        when(httpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("protocol", 0, 1), code, "reason"));
        when(entity.getContent())
                .thenReturn(new ByteArrayInputStream("test".getBytes()));

        assertThrows(exception, () -> client.get(Request.builder().uri(URI.create("127.0.0.1")).build()));
    }
}