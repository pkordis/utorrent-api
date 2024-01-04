package com.utorrent.webapiwrapper.restclient;

import com.google.common.base.Throwables;
import com.utorrent.webapiwrapper.restclient.exceptions.*;
import com.utorrent.webapiwrapper.utils.IOUtils;
import lombok.*;
import org.apache.http.*;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

@Getter
public class RESTClient implements Closeable {
    private final CloseableHttpClient client;
    private final HttpClientContext httpClientContext;
    private final RequestConfig requestConfig;
    private final URI serverURI;

    public RESTClient(final CloseableHttpClient client, final ConnectionParams params, final URI serverURI) {
        requireNonNull(params, "Connection Parameters cannot be null");
        requireNonNull(client, "Client cannot be null");

        this.client = client;
        this.serverURI = serverURI;

        this.httpClientContext = HttpClientContext.create();
        if (nonNull(params.getCredentials())) {
            ConnectionParams.Credentials credentials = params.getCredentials();
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(credentials.getUsername(), credentials.getPassword()));
            httpClientContext.setAttribute(HttpClientContext.CREDS_PROVIDER, credentialsProvider);
        }

        RequestConfig.Builder requestConfigBuilder = RequestConfig.custom()
                .setAuthenticationEnabled(params.isAuthenticationEnabled());

        if (params.getTimeout() > 0) {
            requestConfigBuilder.setSocketTimeout(params.getTimeout())
                    .setConnectTimeout(params.getTimeout())
                    .setConnectionRequestTimeout(params.getTimeout());
        }

        requestConfig = requestConfigBuilder.build();
    }

    public RESTClient(final CloseableHttpClient client, final ConnectionParams params) throws URISyntaxException {
        this(
            client,
            params,
            new URIBuilder()
                .setScheme(params.getScheme())
                .setHost(params.getHost())
                .setPort(params.getPort())
                .setPath("/gui/")
                .build()
        );
    }

    public RESTClient(final ConnectionParams params) throws URISyntaxException {
        this(HttpClients.createDefault(), params);
    }

    public String post(Request request) {
        requireNonNull(request, "Request cannot be null");

        MultipartEntityBuilder httpEntityBuilder = MultipartEntityBuilder.create()
                .setMode(HttpMultipartMode.BROWSER_COMPATIBLE);

        request.getFiles().forEach(file -> httpEntityBuilder.addPart(file.getName(), new FileBody(file.getFile(), file.getContentType())));
        request.getParams().forEach(param -> httpEntityBuilder.addTextBody(param.getName(), param.getValue()));

        HttpUriRequest postRequest = RequestBuilder.post(request.getUri())
                .setEntity(httpEntityBuilder.build())
                .setConfig(requestConfig)
                .build();

        return executeVerb(postRequest);
    }

    public String get(Request request) {
        final URIBuilder uriBuilder = new URIBuilder(request.getUri());
        final RequestBuilder requestBuilder = RequestBuilder.get();
        request.getParams().forEach(param -> uriBuilder.addParameter(param.getName(), param.getValue()));
        request.getHeaders().forEach(requestBuilder::addHeader);
        HttpUriRequest httpUriRequest = null;

        try {
            httpUriRequest = requestBuilder
                    .setUri(uriBuilder.build())
                    .setConfig(requestConfig)
                    .build();
        } catch (URISyntaxException e) {
            Throwables.propagate(e);
        }

        return executeVerb(httpUriRequest);
    }

    private String executeVerb(final HttpUriRequest httpRequest, final Consumer<HttpResponse> responseConsumer) {
        try (final CloseableHttpResponse httpResponse = client.execute(httpRequest, httpClientContext)) {
            String message = IOUtils.toString(httpResponse.getEntity().getContent());
            validateResponse(httpResponse);
            responseConsumer.accept(httpResponse);
            return message;
        } catch (ClientRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new RESTException("Impossible to execute request " + httpRequest.getMethod(), e);
        }
    }

    private String executeVerb(final HttpUriRequest httpRequest) {
        return executeVerb(httpRequest, response -> {});
    }

    @Override
    public void close() throws IOException {
        client.close();
    }

    private void validateResponse(HttpResponse response) {
        requireNonNull(response, "Response from server is null");
        requireNonNull(response.getStatusLine(), "Response status is null");
        if (response.getStatusLine().getStatusCode() > 299) {
            switch (response.getStatusLine().getStatusCode()) {
                case 400:
                    throw new BadRequestException(response.getStatusLine());
                case 401:
                    throw new UnauthorizedException(response.getStatusLine());
                case 403:
                    throw new ForbiddenException(response.getStatusLine());
                case 404:
                    throw new NotFoundException(response.getStatusLine());
                case 406:
                    throw new NotAcceptableException(response.getStatusLine());
                default:
                    throw new ClientRequestException(response.getStatusLine());
            }
        }
    }

    public AuthorizationData authenticate() {
        final HttpUriRequest httpUriRequest = RequestBuilder
            .get()
            .setUri(serverURI.resolve("token.html"))
            .setConfig(requestConfig)
            .build();
        final AtomicReference<String> guidTokenHolder = new AtomicReference<>();
        final String token = executeVerb(
            httpUriRequest,
            response -> {
                final String guid = Arrays
                    .stream(response.getHeaders("Set-Cookie"))
                    .findFirst().flatMap(header -> Arrays
                        .stream(header.getElements())
                        .filter(headerElement -> headerElement.getName().equals("GUID"))
                        .map(HeaderElement::getValue)
                        .findFirst())
                    .orElse(null);
                guidTokenHolder.set(guid);
            }
        ).replaceAll("<[^>]*>", "");
        return new AuthorizationData(token, "GUID=" + guidTokenHolder.get());
    }
}
