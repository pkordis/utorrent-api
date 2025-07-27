package com.utorrent.api.web.client.core;

import com.utorrent.api.web.client.core.entities.MagnetLink;
import com.utorrent.api.web.client.core.entities.RequestResult;
import com.utorrent.api.web.client.core.entities.Torrent;
import com.utorrent.api.web.client.restclient.ConnectionParams;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@Disabled
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LocalUTorrentWebAPIClientImplTest {
    private static UTorrentWebAPIClient CLIENT;
    private static final MagnetLink MAGNET_LINK = MagnetLink
        .manualBuilder()
        .hash("BE85127C6D158AF52B9ABED5760D4E5BFB38C0BE")
        .name("Iron Maiden - Rock am Ring 2014 [2014, Heavy metal, HDTV 720p]")
        .tracker("http://p4p.arenabg.com:1337/announce")
        .tracker("udp://47.ip-51-68-199.eu:6969/announce")
        .tracker("udp://9.rarbg.me:2780/announce")
        .tracker("udp://9.rarbg.to:2710/announce")
        .tracker("udp://9.rarbg.to:2730/announce")
        .tracker("udp://9.rarbg.to:2920/announce")
        .tracker("udp://open.stealth.si:80/announce")
        .tracker("udp://opentracker.i2p.rocks:6969/announce")
        .tracker("udp://tracker.coppersurfer.tk:6969/announce")
        .tracker("udp://tracker.cyberia.is:6969/announce")
        .tracker("udp://tracker.dler.org:6969/announce")
        .tracker("udp://tracker.internetwarriors.net:1337/announce")
        .tracker("udp://tracker.leechers-paradise.org:6969/announce")
        .tracker("udp://tracker.openbittorrent.com:6969/announce")
        .tracker("udp://tracker.opentrackr.org:1337")
        .tracker("udp://tracker.pirateparty.gr:6969/announce")
        .tracker("udp://tracker.tiny-vps.com:6969/announce")
        .tracker("udp://tracker.torrent.eu.org:451/announce")
        .build();

    @BeforeAll
    static void setupClient() {
        ConnectionParams connectionParams = ConnectionParams.builder()
            .withScheme("http")
            .withCredentials("tpb-bot", "tpb-bot")
            .enableAuthentication(true)
            .withAddress("gimli", 13370)
            .withTimeout(1500)
            .create();
        CLIENT = UTorrentWebAPIClient.getClient(connectionParams);
    }

    @Order(1)
    @Test
    void shouldGetAllTorrentsListed() {
        Set<Torrent> allTorrents = CLIENT.getAllTorrents();
    }

    @Order(2)
    @Test
    void shouldAddTheTorrentProvided() throws IOException {
        // Execute
        final RequestResult requestResult = CLIENT.addTorrent(MAGNET_LINK);

        // Verify
        assertThat(requestResult).isSameAs(RequestResult.SUCCESS);
    }

    @Order(3)
    @Test
    void shouldRemoveTheTorrentProvided() {
        // Prepare
        assertDoesNotThrow(() -> CLIENT.getTorrent(MAGNET_LINK.getHash(), 1000, 5));

        // Execute
        final RequestResult requestResult = CLIENT.removeDataTorrent(MAGNET_LINK.getHash());

        // Verify
        assertThat(requestResult).isSameAs(RequestResult.SUCCESS);
    }
}