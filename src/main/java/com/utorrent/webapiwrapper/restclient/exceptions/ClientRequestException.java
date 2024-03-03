package com.utorrent.webapiwrapper.restclient.exceptions;

public class ClientRequestException extends RuntimeException {
    public ClientRequestException(final int statusCode, final String reasonPhrase) {
        super(String.format("Error %d:. %s.", statusCode, reasonPhrase));
    }
}
