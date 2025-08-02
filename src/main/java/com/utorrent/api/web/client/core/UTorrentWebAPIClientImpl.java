package com.utorrent.api.web.client.core;

import com.utorrent.api.web.client.core.entities.ClientSettings;
import com.utorrent.api.web.client.core.entities.MagnetLink;
import com.utorrent.api.web.client.core.entities.Priority;
import com.utorrent.api.web.client.core.entities.RequestResult;
import com.utorrent.api.web.client.core.entities.Torrent;
import com.utorrent.api.web.client.core.entities.TorrentFileList;
import com.utorrent.api.web.client.core.entities.TorrentProperties;
import com.utorrent.api.web.client.restclient.AuthorizationData;
import com.utorrent.api.web.client.restclient.ConnectionParams;
import com.utorrent.api.web.client.restclient.RESTClient;
import com.utorrent.api.web.client.restclient.Request;
import com.utorrent.api.web.client.restclient.Request.FilePart;
import com.utorrent.api.web.client.restclient.Request.QueryParam;
import com.utorrent.api.web.client.restclient.Request.RequestBuilder;
import com.utorrent.api.web.client.restclient.exceptions.BadRequestException;
import com.utorrent.api.web.client.restclient.exceptions.UnauthorizedException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static com.utorrent.api.web.client.core.Action.*;
import static com.utorrent.api.web.client.core.entities.RequestResult.FAIL;
import static com.utorrent.api.web.client.core.entities.RequestResult.SUCCESS;
import static java.lang.String.format;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

@Slf4j
class UTorrentWebAPIClientImpl implements UTorrentWebAPIClient {
    static final String ACTION_QUERY_PARAM_NAME = "action";
    static final String TOKEN_PARAM_NAME = "token";
    static final String URL_PARAM_NAME = "s";
    static final String LIST_QUERY_PARAM_NAME = "list";
    static final String CACHE_ID_QUERY_PARAM = "cid";
    static final String HASH_QUERY_PARAM_NAME = "hash";
    static final String FILE_INDEX_QUERY_PARAM_NAME = "f";
    static final String PRIORITY_QUERY_PARAM_NAME = "p";
    static final String TORRENT_FILE_PART_NAME = "torrent_file";

    private final TorrentsCache torrentsCache;
    private final MessageParser messageParser;
    private final URI serverURI;

    private AuthorizationData authorizationData;
    private RESTClient client;

    UTorrentWebAPIClientImpl(
        final ConnectionParams connectionParams,
        final MessageParser messageParser
    ) {
        resetRestClient(connectionParams);
        this.serverURI = client.getServerURI();
        this.messageParser = messageParser;
        this.torrentsCache = new TorrentsCache();
        log.info("Initialization of Torrent WebAPIClient for server {} was successful", serverURI);
    }

    UTorrentWebAPIClientImpl(
        final MessageParser messageParser,
        final RESTClient client
    ) {
        this.client = client;
        this.serverURI = client.getServerURI();
        this.messageParser = messageParser;
        this.torrentsCache = new TorrentsCache();
    }

    private AuthorizationData getAuthorizationData() {
        if (authorizationData == null) {
            authorizationData = client.authenticate();
            log.info("AuthorizationData: {} ", authorizationData);

            if (authorizationData.getStatus() == AuthorizationData.Status.EXPIRED) {
                log.warn("Session has expired. Resetting client...");
                resetRestClient(client.getConnectionParams());
                authorizationData = client.authenticate();
                log.info("AuthorizationData after authentication with client reset: {} ", authorizationData);

                if (authorizationData.getStatus() != AuthorizationData.Status.OK) {
                    throw new UnauthorizedException(401, "Failed to process the Set-Cookie header part of GUID");
                }
            }
        }
        return authorizationData;
    }

    @SneakyThrows
    void resetRestClient(final ConnectionParams connectionParams) {
        client = new RESTClient(connectionParams);
    }

    private <T> T invokeWithAuthentication(
        final RequestBuilder requestBuilder,
        final Function<Request, T> responseSupplier,
        final boolean retryIfAuthFailed
    ) {
        try {
            final AuthorizationData authData = getAuthorizationData();
            final Request request = requestBuilder
                .param(new QueryParam(TOKEN_PARAM_NAME, authData.getToken()))
                .header("Cookie", authData.getGuidCookie())
                .build();
            final T response = responseSupplier.apply(request);
            requireNonNull(response, format("Received null response from server, request %s", responseSupplier));
            return response;
        } catch (final BadRequestException e) {
            setAuthorizationDataExpired();
            if (retryIfAuthFailed) {
                return invokeWithAuthentication(requestBuilder, responseSupplier, false);
            } else {
                throw new UTorrentAuthException("Impossible to connect to uTorrents, wrong username or password", e);
            }
        }
    }

    @Override
    public RequestResult addTorrent(final MagnetLink magnetLink) {
        final List<Request.QueryParam> params = new ArrayList<>();
        params.add(new Request.QueryParam(URL_PARAM_NAME, magnetLink.asUrlDecodedString()));
        final String result = executeAction(ADD_URL, Collections.emptyList(), params);
        return getResult(result);
    }

    @Override
    public RequestResult addTorrent(File torrentFile) {

        RequestBuilder requestBuilder = Request.builder()
                .uri(serverURI)
                .param(new Request.QueryParam(ACTION_QUERY_PARAM_NAME, ADD_FILE.getName()))
                .file(new FilePart(TORRENT_FILE_PART_NAME, torrentFile, APPLICATION_X_BIT_TORRENT_CONTENT_TYPE));

        String stringResult = invokeWithAuthentication(requestBuilder, client::post, true);
        return getResult(stringResult);
    }

    @Override
    public Set<Torrent> getAllTorrents() {
        updateTorrentCache();
        return torrentsCache.getTorrentList();
    }

    private void updateTorrentCache() {
        RequestBuilder requestBuilder = Request.builder()
                .uri(serverURI)
                .param(new QueryParam(LIST_QUERY_PARAM_NAME, "1"));

        if (nonNull(torrentsCache.getCachedID())) {
            requestBuilder.param(new QueryParam(CACHE_ID_QUERY_PARAM, torrentsCache.getCachedID()));
        }

        String jsonTorrentSnapshotMessage = invokeWithAuthentication(requestBuilder, client::get, true);
        torrentsCache.updateCache(messageParser.parseAsTorrentListSnapshot(jsonTorrentSnapshotMessage));
    }

    @Override
    public Torrent getTorrent(String torrentHash) {
        updateTorrentCache();
        return torrentsCache.getTorrent(torrentHash);
    }

    @Override
    public Torrent getTorrent(final String torrentHash, final long delay, final int retries) {
        Optional<Torrent> torrentMatched;
        int timesRetried = -1;
        do {
            torrentMatched = this
                .getAllTorrents()
                .stream()
                .filter(torrent -> torrent.getHash().equals(torrentHash))
                .findFirst();
            ++timesRetried;
            if (torrentMatched.isPresent() || timesRetried == retries) {
                break;
            } else {
                try {
                    Thread.sleep(delay);
                } catch (final InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        } while (true);
        return torrentMatched.orElseThrow(() -> new RuntimeException("Condition not met within time window"));
    }

    @Override
    public Set<TorrentFileList> getTorrentFiles(List<String> torrentHashes) {
        String torrentFilesJsonMessage = executeAction(GET_FILES, torrentHashes, List.of());
        return messageParser.parseAsTorrentFileList(torrentFilesJsonMessage);
    }

    @Override
    public Optional<TorrentFileList> getTorrentFiles(String torrentHash) {
        return getTorrentFiles(List.of(torrentHash)).stream().findFirst();
    }

    @Override
    public Set<TorrentProperties> getTorrentProperties(List<String> torrentHashes) {
        String jsonTorrentPropertiesMessage = executeAction(GET_PROP, torrentHashes, List.of());
        return messageParser.parseAsTorrentProperties(jsonTorrentPropertiesMessage);
    }

    @Override
    public Optional<TorrentProperties> getTorrentProperties(String torrentHash) {
        return getTorrentProperties(List.of(torrentHash)).stream().findFirst();
    }

    @Override
    public RequestResult startTorrent(List<String> hashes) {
        return executeBaseTorrentAction(START, hashes);
    }

    @Override
    public RequestResult startTorrent(String hash) {
        return startTorrent(List.of(hash));
    }

    @Override
    public RequestResult stopTorrent(List<String> hash) {
        return executeBaseTorrentAction(STOP, hash);
    }

    @Override
    public RequestResult stopTorrent(String hash) {
        return stopTorrent(List.of(hash));
    }

    @Override
    public RequestResult pauseTorrent(List<String> hash) {
        return executeBaseTorrentAction(PAUSE, hash);
    }

    @Override
    public RequestResult pauseTorrent(String hash) {
        return pauseTorrent(List.of(hash));
    }

    @Override
    public RequestResult forceStartTorrent(List<String> hash) {
        return executeBaseTorrentAction(FORCE_START, hash);
    }

    @Override
    public RequestResult forceStartTorrent(String hash) {
        return forceStartTorrent(List.of(hash));
    }

    @Override
    public RequestResult unpauseTorrent(List<String> hash) {
        return executeBaseTorrentAction(UN_PAUSE, hash);
    }

    @Override
    public RequestResult unpauseTorrent(String hash) {
        return unpauseTorrent(List.of(hash));
    }

    @Override
    public RequestResult recheckTorrent(List<String> hash) {
        return executeBaseTorrentAction(RECHECK, hash);
    }

    @Override
    public RequestResult recheckTorrent(String hash) {
        return recheckTorrent(List.of(hash));
    }

    @Override
    public RequestResult removeTorrent(List<String> hash) {
        return executeBaseTorrentAction(REMOVE, hash);
    }

    @Override
    public RequestResult removeTorrent(String hash) {
        return removeTorrent(List.of(hash));
    }

    @Override
    public RequestResult removeDataTorrent(List<String> hash) {
        return executeBaseTorrentAction(REMOVE_DATA, hash);
    }

    @Override
    public RequestResult removeDataTorrent(String hash) {
        return removeDataTorrent(List.of(hash));
    }

    @Override
    public RequestResult queueBottomTorrent(List<String> hash) {
        return executeBaseTorrentAction(QUEUE_BOTTOM, hash);
    }

    @Override
    public RequestResult queueBottomTorrent(String hash) {
        return queueBottomTorrent(List.of(hash));
    }

    @Override
    public RequestResult queueUpTorrent(List<String> hash) {
        return executeBaseTorrentAction(QUEUE_UP, hash);
    }

    @Override
    public RequestResult queueUpTorrent(String hash) {
        return queueUpTorrent(List.of(hash));
    }

    @Override
    public RequestResult queueDownTorrent(List<String> hash) {
        return executeBaseTorrentAction(QUEUE_DOWN, hash);
    }

    @Override
    public RequestResult queueDownTorrent(String hash) {
        return queueDownTorrent(List.of(hash));
    }

    @Override
    public RequestResult queueTopTorrent(List<String> hash) {
        return executeBaseTorrentAction(QUEUE_TOP, hash);
    }

    @Override
    public RequestResult queueTopTorrent(String hash) {
        return queueTopTorrent(List.of(hash));
    }

    @Override
    public RequestResult setTorrentFilePriority(String hash, Priority priority,
                                                List<Integer> fileIndices) {
        List<Request.QueryParam> params = new ArrayList<>();
        params.add(new Request.QueryParam(PRIORITY_QUERY_PARAM_NAME, String.valueOf(priority.getValue())));
        fileIndices.forEach(index -> params.add(new Request.QueryParam(FILE_INDEX_QUERY_PARAM_NAME, String.valueOf(index))));
        return getResult(executeAction(SET_PRIORITY, List.of(hash), params));
    }

    @Override
    public RequestResult setClientSetting(SettingsKey settingKey, String settingValue) {
        return setClientSetting(settingKey.getKeyValue(), settingValue);
    }

    @Override
    public RequestResult setClientSetting(String settingName, String settingValue) {
        return setClientSetting(List.of(new Request.QueryParam(settingName, settingValue)));
    }

    @Override
    public RequestResult setClientSetting(List<Request.QueryParam> settings) {
        return getResult(executeAction(SET_SETTING, List.of(), settings));
    }

    @Override
    public ClientSettings getClientSettings() {
        String returnedValue = executeAction(GET_SETTINGS);
        return messageParser.parseAsClientSettings(returnedValue);
    }

    private RequestResult executeBaseTorrentAction(Action action, List<String> hashes) {
        return getResult(executeAction(action, hashes, List.of()));
    }

    private String executeAction(Action action) {
        return executeAction(action, List.of(), List.of());
    }

    private String executeAction(Action action, List<String> torrentHashes, List<Request.QueryParam> queryParams) {
        final RequestBuilder requestBuilder = Request
            .builder()
            .uri(serverURI)
            .param(new QueryParam(ACTION_QUERY_PARAM_NAME, action.getName()));

        queryParams.forEach(param -> requestBuilder.param(new QueryParam(param.getName(), param.getValue())));
        torrentHashes.forEach(hash -> requestBuilder.param(new QueryParam(HASH_QUERY_PARAM_NAME, hash)));
        return invokeWithAuthentication(requestBuilder, client::get, true);
    }

    private void setAuthorizationDataExpired() {
        authorizationData = null;
    }

    @Override
    public void close() throws IOException {
        this.client.close();
    }

    private RequestResult getResult(String result) {
        return nonNull(result) && result.contains("build") ? SUCCESS : FAIL;
    }
}
