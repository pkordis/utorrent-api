package com.utorrent.api.web.client.restclient;

import lombok.Data;

@Data
public class AuthorizationData {
    private final String token;
    private final String guidCookie;
}
