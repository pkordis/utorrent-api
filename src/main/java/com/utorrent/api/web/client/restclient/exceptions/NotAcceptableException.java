package com.utorrent.api.web.client.restclient.exceptions;

public class NotAcceptableException extends ClientRequestException {
    public NotAcceptableException(final int statusCode, final String reasonPhrase) {
        super(statusCode, reasonPhrase);
    }
}
