package com.utorrent.webapiwrapper.restclient.exceptions;

public class BadRequestException extends ClientRequestException {
    public BadRequestException(final int statusCode, final String reasonPhrase) {
        super(statusCode, reasonPhrase);
    }
}
