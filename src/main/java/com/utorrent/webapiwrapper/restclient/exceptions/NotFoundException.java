package com.utorrent.webapiwrapper.restclient.exceptions;

public class NotFoundException extends ClientRequestException {
    public NotFoundException(final int statusCode, final String reasonPhrase) {
        super(statusCode, reasonPhrase);
    }
}
