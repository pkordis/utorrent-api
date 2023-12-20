package com.utorrent.webapiwrapper.restclient;

import lombok.Data;

@Data
public class AuthorizationData {
    private final String token;
    private final String guidCookie;
}
