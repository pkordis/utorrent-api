package com.utorrent.api.web.client.restclient.exceptions;

public class ForbiddenException extends ClientRequestException {
    public ForbiddenException(final int statusCode, final String reasonPhrase) {
        super(statusCode, reasonPhrase);
    }
}
