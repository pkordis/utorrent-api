package com.utorrent.api.web.client.core.entities;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Data
@Slf4j
@Builder(access = AccessLevel.PRIVATE)
public class MagnetLink {
    private static final Pattern ID_PATTERN = Pattern.compile("[0-9,A-F]{40}");
    private static final Pattern URN_PATTERN = Pattern.compile("urn:btih:(" + ID_PATTERN + ")");
    private final String hash;
    private final String name;
    @Accessors(fluent = true)
    private final String asUrlEncodedString;
    @Accessors(fluent = true)
    private final String asUrlDecodedString;
    @Singular
    private final List<URI> trackers;

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ManuallyMagnetLinkBuilder {
        private String hash;
        private String name;
        private final List<URI> trackers = new ArrayList<>();

        public ManuallyMagnetLinkBuilder hash(final String hash) {
            final Matcher idMatcher = ID_PATTERN.matcher(hash);
            if (!idMatcher.matches()) {
                throw new IllegalArgumentException("The hash must be a 40 character-long uppercase hexadecimal");
            }
            this.hash = hash;
            return this;
        }

        public ManuallyMagnetLinkBuilder name(final String name) {
            this.name = name;
            return this;
        }

        public ManuallyMagnetLinkBuilder tracker(final String tracker) {
            this.trackers.add(URI.create(tracker));
            return this;
        }

        @SuppressWarnings("squid:S112")
        public MagnetLink build() {
            final StringBuilder sb = new StringBuilder("xt=urn:btih:")
                .append(this.hash)
                .append("&dn=")
                .append(this.name);
            this.trackers.forEach(tracker -> sb.append("&tr=").append(tracker.toString()));
            final String urlEncodedMagnetLink = URLEncoder.encode(sb.toString(), StandardCharsets.UTF_8);
            try {
                return MagnetLink.parse("magnet:?" + urlEncodedMagnetLink);
            } catch (final URISyntaxException e) {
                log.error("An exception was not expected at this stage", e);
                throw new RuntimeException(e);
            }
        }
    }

    public static ManuallyMagnetLinkBuilder manualBuilder() {
        return new ManuallyMagnetLinkBuilder();
    }

    public static MagnetLink parse(final String urlEncodedMagnetLink) throws URISyntaxException {
        final MagnetLinkBuilder builder = MagnetLink.builder();
        final String urlDecodedMagnetLink = URLDecoder.decode(urlEncodedMagnetLink, StandardCharsets.UTF_8);
        try {
            if (!urlDecodedMagnetLink.startsWith("magnet:?")) {
                throw new ParseException("The scheme is not magnet", 0);
            }
            final String[] linkTokens = urlDecodedMagnetLink.split("&");
            linkTokens[0] = linkTokens[0].replaceFirst("magnet:\\?", "");
            Arrays.stream(linkTokens)
                .map(token -> token.split("="))
                .filter(tokens -> tokens.length == 2)
                .map(tokens -> new AbstractMap.SimpleEntry<>(tokens[0], tokens[1]))
                .forEach(token -> {
                    switch (token.getKey()) {
                        case "tr":
                            final URI tracker = URI.create(token.getValue());
                            builder.tracker(tracker);
                            break;
                        case "xt":
                            final String urn = token.getValue();
                            final Matcher urnPatternMatcher = URN_PATTERN.matcher(urn);
                            if (urnPatternMatcher.matches()) {
                                final String id = urnPatternMatcher.group(1);
                                builder.hash(id);
                            } else {
                                log.warn("Failed to parse the URN: {}", urn);
                            }
                            break;
                        case "dn":
                            builder.name(token.getValue());
                            break;
                        default:
                            log.warn("URL parameter: '{}' omitted as it is not recognized", token.getKey());
                    }
                });
        } catch (final Exception e) {
            throw new URISyntaxException(urlDecodedMagnetLink, "Not a magnet link");
        }
        return builder
            .asUrlEncodedString(urlEncodedMagnetLink)
            .asUrlDecodedString(urlDecodedMagnetLink)
            .build();
    }
}
