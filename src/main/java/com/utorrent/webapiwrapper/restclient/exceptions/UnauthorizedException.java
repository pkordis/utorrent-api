package com.utorrent.webapiwrapper.restclient.exceptions;

public class UnauthorizedException extends ClientRequestException {
    public UnauthorizedException(final int statusCode, final String reasonPhrase) {
        super(statusCode, reasonPhrase);
    }
}
