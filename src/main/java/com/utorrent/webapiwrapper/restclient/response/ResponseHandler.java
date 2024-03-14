package com.utorrent.webapiwrapper.restclient.response;

import com.utorrent.webapiwrapper.restclient.exceptions.*;
import com.utorrent.webapiwrapper.utils.IOUtils;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;

import java.io.IOException;

import static java.util.Objects.requireNonNull;

public class ResponseHandler implements HttpClientResponseHandler<String> {
    @Override
    public String handleResponse(final ClassicHttpResponse httpResponse) throws IOException {
        validateResponse(httpResponse);
        return IOUtils.toString(httpResponse.getEntity().getContent());
    }

    void validateResponse(ClassicHttpResponse response) {
        requireNonNull(response, "Response from server is null");
        if (response.getCode() > 299) {
            switch (response.getCode()) {
                case 400:
                    throw new BadRequestException(response.getCode(), response.getReasonPhrase());
                case 401:
                    throw new UnauthorizedException(response.getCode(), response.getReasonPhrase());
                case 403:
                    throw new ForbiddenException(response.getCode(), response.getReasonPhrase());
                case 404:
                    throw new NotFoundException(response.getCode(), response.getReasonPhrase());
                case 406:
                    throw new NotAcceptableException(response.getCode(), response.getReasonPhrase());
                default:
                    throw new ClientRequestException(response.getCode(), response.getReasonPhrase());
            }
        }
    }
}
