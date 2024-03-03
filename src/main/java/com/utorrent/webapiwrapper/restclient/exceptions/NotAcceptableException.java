package com.utorrent.webapiwrapper.restclient.exceptions;

public class NotAcceptableException extends ClientRequestException {
    public NotAcceptableException(final int statusCode, final String reasonPhrase) {
        super(statusCode, reasonPhrase);
    }
}
