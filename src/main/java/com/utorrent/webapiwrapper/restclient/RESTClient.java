package com.utorrent.webapiwrapper.restclient;

import com.utorrent.webapiwrapper.restclient.exceptions.ClientRequestException;
import com.utorrent.webapiwrapper.restclient.exceptions.RESTException;
import com.utorrent.webapiwrapper.restclient.exceptions.UnauthorizedException;
import com.utorrent.webapiwrapper.restclient.response.ResponseHandler;
import lombok.Getter;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.mime.FileBody;
import org.apache.hc.client5.http.entity.mime.HttpMultipartMode;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.net.URIBuilder;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

@Getter
public class RESTClient implements Closeable {
    private final HttpClientResponseHandler<String> standardResponseHandler;
    private final CloseableHttpClient client;
    private final HttpClientContext httpClientContext;
    private final URI serverURI;

    public RESTClient(
        final CloseableHttpClient client,
        final ConnectionParams params,
        final URI serverURI,
        final HttpClientResponseHandler<String> responseHandler
    ) {
        requireNonNull(params, "Connection Parameters cannot be null");
        requireNonNull(client, "Client cannot be null");

        this.client = client;
        this.serverURI = serverURI;

        this.httpClientContext = HttpClientContext.create();
        if (nonNull(params.getCredentials())) {
            ConnectionParams.Credentials credentials = params.getCredentials();
            BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                new AuthScope(null, -1),
                new UsernamePasswordCredentials(credentials.getUsername(), credentials.getPassword().toCharArray())
            );
            httpClientContext.setAttribute(HttpClientContext.CREDS_PROVIDER, credentialsProvider);
        }

        RequestConfig.Builder requestConfigBuilder = RequestConfig.custom()
                .setAuthenticationEnabled(params.isAuthenticationEnabled());

        if (params.getTimeout() > 0) {
            requestConfigBuilder
                .setResponseTimeout(params.getTimeout(), TimeUnit.MILLISECONDS)
                .setConnectionRequestTimeout(params.getTimeout(), TimeUnit.MILLISECONDS);
        }

        httpClientContext.setAttribute(HttpClientContext.REQUEST_CONFIG, requestConfigBuilder.build());
        this.standardResponseHandler = responseHandler;
    }

    public RESTClient(
        final CloseableHttpClient client,
        final ConnectionParams params,
        final HttpClientResponseHandler<String> responseHandler
    ) throws URISyntaxException {
        this(
            client,
            params,
            new URIBuilder()
                .setScheme(params.getScheme())
                .setHost(params.getHost())
                .setPort(params.getPort())
                .setPath("/gui/")
                .build(),
            responseHandler
        );
    }

    public RESTClient(final ConnectionParams params) throws URISyntaxException {
        this(HttpClients.createDefault(), params, new ResponseHandler());
    }

    public String post(Request request) {
        requireNonNull(request, "Request cannot be null");

        MultipartEntityBuilder httpEntityBuilder = MultipartEntityBuilder.create()
                .setMode(HttpMultipartMode.LEGACY);

        request.getFiles().forEach(file -> httpEntityBuilder.addPart(file.getName(), new FileBody(file.getFile(), file.getContentType())));
        request.getParams().forEach(param -> httpEntityBuilder.addTextBody(param.getName(), param.getValue()));

        final ClassicHttpRequest postRequest = ClassicRequestBuilder.post(request.getUri())
                .setEntity(httpEntityBuilder.build())
                .build();

        return executeVerb(postRequest);
    }

    public String get(Request request) {
        final URIBuilder uriBuilder = new URIBuilder(request.getUri());
        final ClassicRequestBuilder requestBuilder = ClassicRequestBuilder.get();
        request.getParams().forEach(param -> uriBuilder.addParameter(param.getName(), param.getValue()));
        request.getHeaders().forEach(requestBuilder::addHeader);

        try {
            final ClassicHttpRequest httpUriRequest = requestBuilder.setUri(uriBuilder.build()).build();
            return executeVerb(httpUriRequest);
        } catch (final URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private <T> T executeVerb(
        final ClassicHttpRequest httpRequest,
        final HttpClientResponseHandler<T> responseHandler
    ) {
        try {
            return client.execute(httpRequest, httpClientContext, responseHandler);
        } catch (ClientRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new RESTException("Impossible to execute request " + httpRequest.getMethod(), e);
        }
    }

    private String executeVerb(final ClassicHttpRequest httpRequest) {
        return executeVerb(httpRequest, standardResponseHandler);
    }

    @Override
    public void close() throws IOException {
        client.close();
    }

    public AuthorizationData authenticate() {
        final ClassicHttpRequest httpUriRequest = ClassicRequestBuilder
            .get()
            .setUri(serverURI.resolve("token.html"))
            .build();
        return executeVerb(
            httpUriRequest,
            response -> {
                try {
                    final String setCookieValue = response.getHeader("Set-Cookie").getValue();
                    final String guid = Arrays
                        .stream(setCookieValue.split(";\\s*"))
                        .filter(headerElement -> headerElement.startsWith("GUID"))
                        .map(headerElement -> headerElement.split("=")[1])
                        .findFirst()
                        .orElse(null);
                    final String token = standardResponseHandler.handleResponse(response).replaceAll("<[^>]*>", "");
                    return new AuthorizationData(token, "GUID=" + guid);
                } catch (final Exception e) {
                    throw new UnauthorizedException(401, "Failed to process the Set-Cookie header part of GUID");
                }
            }
        );

    }
}
