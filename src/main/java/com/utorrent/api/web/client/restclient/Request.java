package com.utorrent.api.web.client.restclient;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.Singular;
import org.apache.hc.core5.http.ContentType;

import java.io.File;
import java.net.URI;
import java.util.*;

@Data
@Builder
public class Request {
    private final URI uri;
    @Singular
    private final Set<QueryParam> params;
    @Singular
    private final Map<String, String> headers;
    @Singular
    private final Set<FilePart> files;

    @Data
    @RequiredArgsConstructor
    public static class FilePart {
        private final String name;
        private final File file;
        private final ContentType contentType;
    }

    @Data
    public static class QueryParam {
        private final String name;
        private final String value;
    }
}
