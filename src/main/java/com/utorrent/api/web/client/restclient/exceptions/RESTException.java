package com.utorrent.api.web.client.restclient.exceptions;

public class RESTException extends RuntimeException{
    public RESTException(String message, Exception parentException) {
        super(message, parentException);
    }
}
