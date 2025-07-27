package com.utorrent.api.web.client.core;

public class UTorrentAuthException extends RuntimeException {
    public UTorrentAuthException(
        final String msg,
        final Throwable throwable
    ) {
        super(msg, throwable);
    }
}
