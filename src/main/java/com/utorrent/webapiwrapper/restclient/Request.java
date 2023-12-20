package com.utorrent.webapiwrapper.restclient;

import lombok.*;
import org.apache.http.entity.ContentType;

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
