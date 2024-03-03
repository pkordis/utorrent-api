package com.utorrent.webapiwrapper.restclient.exceptions;

public class ForbiddenException extends ClientRequestException {
    public ForbiddenException(final int statusCode, final String reasonPhrase) {
        super(statusCode, reasonPhrase);
    }
}
