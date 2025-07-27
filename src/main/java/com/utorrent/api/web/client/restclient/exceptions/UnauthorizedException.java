package com.utorrent.api.web.client.restclient.exceptions;

public class UnauthorizedException extends ClientRequestException {
    public UnauthorizedException(final int statusCode, final String reasonPhrase) {
        super(statusCode, reasonPhrase);
    }
}
