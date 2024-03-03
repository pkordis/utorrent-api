package com.utorrent.webapiwrapper.core;

import com.utorrent.webapiwrapper.core.entities.ClientSettings;
import com.utorrent.webapiwrapper.core.entities.MagnetLink;
import com.utorrent.webapiwrapper.core.entities.Priority;
import com.utorrent.webapiwrapper.core.entities.RequestResult;
import com.utorrent.webapiwrapper.core.entities.Torrent;
import com.utorrent.webapiwrapper.core.entities.TorrentFileList;
import com.utorrent.webapiwrapper.core.entities.TorrentListSnapshot;
import com.utorrent.webapiwrapper.core.entities.TorrentProperties;
import com.utorrent.webapiwrapper.restclient.AuthorizationData;
import com.utorrent.webapiwrapper.restclient.ConnectionParams;
import com.utorrent.webapiwrapper.restclient.RESTClient;
import com.utorrent.webapiwrapper.restclient.Request;
import com.utorrent.webapiwrapper.restclient.exceptions.BadRequestException;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.message.BasicStatusLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.utorrent.webapiwrapper.core.UTorrentWebAPIClientImpl.URL_PARAM_NAME;
import static com.utorrent.webapiwrapper.restclient.Request.FilePart;
import static com.utorrent.webapiwrapper.restclient.Request.QueryParam;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({
    MockitoExtension.class
})
class UTorrentWebAPIClientImplTest {

    @Mock
    private RESTClient restClient;
    @Mock
    private MessageParser parser;

    private UTorrentWebAPIClientImpl client;

    private URI serverURI;

    @BeforeEach
    void beforeEach() throws Exception {
        ConnectionParams connectionParams = ConnectionParams.builder()
                .withScheme("http")
                .withCredentials("username", "password")
                .withAddress("host.com", 8080)
                .withTimeout(1500)
                .create();

        serverURI = new URIBuilder()
                .setScheme(connectionParams.getScheme())
                .setHost(connectionParams.getHost())
                .setPort(connectionParams.getPort())
                .setPath("/gui/").build();

        when(restClient.getServerURI()).thenReturn(serverURI);
        client = new UTorrentWebAPIClientImpl(parser, restClient);
        final AuthorizationData authorizationData = new AuthorizationData(TOKEN_VALUE, "GUID");
        when(restClient.authenticate()).thenReturn(authorizationData);
    }

    @Test
    void testInvokeWithAuthentication() {
        File torrentFile = new File("fakePath");
        when(restClient.post(any())).thenReturn(BUILD_STRING);
        StatusLine statusLine = new BasicStatusLine(new ProtocolVersion("protocol", 0, 1), 5, "reason");
        client.addTorrent(torrentFile);
        when(restClient.post(any())).thenThrow(new BadRequestException(statusLine));
        assertThatThrownBy(() -> client.addTorrent(torrentFile))
                .isInstanceOf(UTorrentAuthException.class)
                .hasRootCauseInstanceOf(BadRequestException.class)
                .hasMessage("Impossible to connect to uTorrents, wrong username or password");
        verify(restClient, never()).get(any());
    }

    @Test
    void testAddTorrentWithFileSuccess() throws Exception {
        File torrentFile = new File("fakePath");

        ArgumentCaptor<Request> argumentCaptor = ArgumentCaptor.forClass(Request.class);
        when(restClient.post(argumentCaptor.capture())).thenReturn(BUILD_STRING);

        RequestResult requestResult = client.addTorrent(torrentFile);
        assertThat(requestResult).isEqualTo(RequestResult.SUCCESS);

        Request request = argumentCaptor.getValue();
        FilePart filePart = new FilePart("torrent_file", torrentFile, UTorrentWebAPIClient.APPLICATION_X_BIT_TORRENT_CONTENT_TYPE);

        validateRequest(Action.ADD_FILE, request, List.of(), List.of(filePart));
    }

    @Test
    void testAddTorrentWithFileFail() throws Exception {
        File torrentFile = new File("fakePath");

        ArgumentCaptor<Request> argumentCaptor = ArgumentCaptor.forClass(Request.class);
        when(restClient.post(argumentCaptor.capture())).thenReturn("wrong");

        RequestResult requestResult = client.addTorrent(torrentFile);
        assertThat(requestResult).isEqualTo(RequestResult.FAIL);
    }

    @Test
    void testAddTorrentWithStringPathSuccess() throws Exception {
        final MagnetLink magnetLink = MagnetLink
            .manualBuilder()
            .name("foo")
            .hash("0123456789ABCDEF0123456789ABCDEF01234567")
            .tracker("udp://the.1337.house:1337/announce")
            .build();
        ArgumentCaptor<Request> argumentCaptor = ArgumentCaptor.forClass(Request.class);
        when(restClient.get(argumentCaptor.capture())).thenReturn(TOKEN_VALUE);
        client.addTorrent(magnetLink);

        when(restClient.get(argumentCaptor.capture())).thenReturn(BUILD_STRING);
        RequestResult requestResult = client.addTorrent(magnetLink);
        assertThat(requestResult).isEqualTo(RequestResult.SUCCESS);

        final QueryParam expectedURL = new QueryParam(URL_PARAM_NAME, magnetLink.asUrlDecodedString());

        Request actualRequest = argumentCaptor.getValue();
        validateRequest(Action.ADD_URL, actualRequest, List.of(expectedURL));
    }

    @Test
    void testGetTorrentList() throws Exception {
        TorrentListSnapshot torrentListSnapshot = new TorrentListSnapshot();
        when(parser.parseAsTorrentListSnapshot(anyString())).thenReturn(torrentListSnapshot);
        when(restClient.get(any(Request.class))).thenReturn(TOKEN_VALUE);
        client.getAllTorrents();

        ArgumentCaptor<Request> argumentCaptor = ArgumentCaptor.forClass(Request.class);
        when(restClient.get(argumentCaptor.capture())).thenReturn(BUILD_STRING);
        client.getAllTorrents();

        Request requestToValidate = argumentCaptor.getValue();
        assertThat(requestToValidate).isNotNull();

        List<QueryParam> queryParamsToCompare = List.of(
            new QueryParam(UTorrentWebAPIClientImpl.TOKEN_PARAM_NAME, TOKEN_VALUE),
            new QueryParam(UTorrentWebAPIClientImpl.LIST_QUERY_PARAM_NAME, "1")
        );

        assertThat(requestToValidate.getUri()).isEqualTo(serverURI);
        assertThat(requestToValidate.getParams()).hasSameElementsAs(queryParamsToCompare);

        Set<Torrent> torrentList = client.getAllTorrents();
        assertThat(torrentList).isEmpty();

    }

    @Test
    void testGetTorrentFiles() throws Exception {
        String nameFirstTorrent = "file_1";
        TorrentFileList.File firstTorrent = TorrentFileList.File.builder().name(nameFirstTorrent).build();
        String nameSecondTorrent = "file_2";
        TorrentFileList.File secondTorrent = TorrentFileList.File.builder().name(nameSecondTorrent).build();
        when(restClient.get(any(Request.class))).thenReturn(TOKEN_VALUE);
        client.getTorrentFiles(List.of());

        TorrentFileList torrentFileList = new TorrentFileList();
        torrentFileList.addFile(firstTorrent);
        torrentFileList.addFile(secondTorrent);
        torrentFileList.setHash(HASH_1);
        ArgumentCaptor<Request> requestArgumentCaptor = ArgumentCaptor.forClass(Request.class);
        when(restClient.get(requestArgumentCaptor.capture())).thenReturn(HASH_1);
        when(parser.parseAsTorrentFileList(HASH_1)).thenReturn(Set.of(torrentFileList));

        Optional<TorrentFileList> result = client.getTorrentFiles(HASH_1);

        QueryParam expectedHash = new QueryParam(UTorrentWebAPIClientImpl.HASH_QUERY_PARAM_NAME, HASH_1);

        Request actualRequest = requestArgumentCaptor.getValue();
        validateRequest(Action.GET_FILES, actualRequest, List.of(expectedHash));

        assertThat(result)
            .isPresent()
            .containsSame(torrentFileList);
        verify(restClient, times(2)).get(any());
    }

    @Test
    void testGetTorrentProperties() throws Exception {
        TorrentProperties torrentPropertiesExpected = TorrentProperties.builder().build();
        when(restClient.get(any(Request.class))).thenReturn(TOKEN_VALUE);
        when(parser.parseAsTorrentProperties(anyString())).thenReturn(Set.of(torrentPropertiesExpected));
        client.getTorrentProperties(HASH_1);

        ArgumentCaptor<Request> requestArgumentCaptor = ArgumentCaptor.forClass(Request.class);
        when(restClient.get(requestArgumentCaptor.capture())).thenReturn(BUILD_STRING);
        Optional<TorrentProperties> actualTorrentProperties = client.getTorrentProperties(HASH_1);

        Request actualRequest = requestArgumentCaptor.getValue();
        validateRequest(Action.GET_PROP, actualRequest, List.of(new QueryParam(UTorrentWebAPIClientImpl.HASH_QUERY_PARAM_NAME, HASH_1)));

        assertThat(actualTorrentProperties).isPresent();
        assertThat(actualTorrentProperties.get()).isSameAs(torrentPropertiesExpected);
        verify(restClient, times(2)).get(any());

    }

    @Test
    void testStartTorrent() throws Exception {
        testSimpleTorrentAction(Action.START, client::startTorrent);
    }

    @Test
    void testStopTorrent() throws Exception {
        testSimpleTorrentAction(Action.STOP, client::stopTorrent);
    }

    @Test
    void testPauseTorrent() throws Exception {
        testSimpleTorrentAction(Action.PAUSE, client::pauseTorrent);
    }

    @Test
    void testForceStartTorrent() throws Exception {
        testSimpleTorrentAction(Action.FORCE_START, client::forceStartTorrent);
    }

    @Test
    void testUnpauseTorrent() throws Exception {
        testSimpleTorrentAction(Action.UN_PAUSE, client::unpauseTorrent);
    }

    @Test
    void testRecheckTorrent() throws Exception {
        testSimpleTorrentAction(Action.RECHECK, client::recheckTorrent);
    }

    @Test
    void testRemoveTorrent() throws Exception {
        testSimpleTorrentAction(Action.REMOVE, client::removeTorrent);
    }

    @Test
    void testRemoveDataTorrent() throws Exception {
        testSimpleTorrentAction(Action.REMOVE_DATA, client::removeDataTorrent);
    }

    @Test
    void testQueueTopTorrent() throws Exception {
        testSimpleTorrentAction(Action.QUEUE_TOP, client::queueTopTorrent);
    }

    @Test
    void testQueueUpTorrent() throws Exception {
        testSimpleTorrentAction(Action.QUEUE_UP, client::queueUpTorrent);
    }

    @Test
    void testQueueDownTorrent() throws Exception {
        testSimpleTorrentAction(Action.QUEUE_DOWN, client::queueDownTorrent);
    }

    @Test
    void testQueueBottomTorrent() throws Exception {
        testSimpleTorrentAction(Action.QUEUE_BOTTOM, client::queueBottomTorrent);
    }

    @Test
    void testSetTorrentFilePriority() throws Exception {
        final Priority priority = Priority.HIGH_PRIORITY;

        when(restClient.get(any(Request.class))).thenReturn(TOKEN_VALUE);
        client.setTorrentFilePriority(HASH_1, priority, List.of(1, 2, 3));

        ArgumentCaptor<Request> requestArgumentCaptor = ArgumentCaptor.forClass(Request.class);
        when(restClient.get(requestArgumentCaptor.capture())).thenReturn(BUILD_STRING);
        RequestResult requestResult = client.setTorrentFilePriority(HASH_1, priority, List.of(1, 2, 3));
        assertThat(requestResult).isEqualTo(RequestResult.SUCCESS);

        List<QueryParam> queryParams = List.of(
            new QueryParam(UTorrentWebAPIClientImpl.PRIORITY_QUERY_PARAM_NAME, String.valueOf(priority.getValue())),
            new QueryParam(UTorrentWebAPIClientImpl.FILE_INDEX_QUERY_PARAM_NAME, String.valueOf(1)),
            new QueryParam(UTorrentWebAPIClientImpl.FILE_INDEX_QUERY_PARAM_NAME, String.valueOf(2)),
            new QueryParam(UTorrentWebAPIClientImpl.FILE_INDEX_QUERY_PARAM_NAME, String.valueOf(3)),
            new QueryParam(UTorrentWebAPIClientImpl.HASH_QUERY_PARAM_NAME, HASH_1)
        );

        Request actualRequest = requestArgumentCaptor.getValue();

        validateRequest(Action.SET_PRIORITY, actualRequest, queryParams);

        verify(restClient, times(2)).get(any());
    }

    @Test
    void testGetClientSetting() {
        when(restClient.get(any(Request.class))).thenReturn(TOKEN_VALUE);
        client.getClientSettings();

        ClientSettings expectedClientSettings = new ClientSettings();

        ArgumentCaptor<Request> requestArgumentCaptor = ArgumentCaptor.forClass(Request.class);
        when(restClient.get(requestArgumentCaptor.capture())).thenReturn(BUILD_STRING);
        when(parser.parseAsClientSettings(BUILD_STRING)).thenReturn(expectedClientSettings);

        ClientSettings actualClientSettings = client.getClientSettings();
        assertThat(actualClientSettings).isSameAs(expectedClientSettings);

        validateRequest(Action.GET_SETTINGS, requestArgumentCaptor.getValue(), List.of());
    }

    @Test
    void testSetClientSetting() throws Exception {
        when(restClient.get(any(Request.class))).thenReturn(TOKEN_VALUE);
        client.setClientSetting(SettingsKey.BOSS_KEY, "value");

        ArgumentCaptor<Request> requestArgumentCaptor = ArgumentCaptor.forClass(Request.class);
        when(restClient.get(requestArgumentCaptor.capture())).thenReturn(BUILD_STRING);
        List<QueryParam> setting = List.of(new QueryParam(SettingsKey.BOSS_KEY.getKeyValue(), "value"));
        RequestResult requestResult = client.setClientSetting(setting);

        assertThat(requestResult).isEqualTo(RequestResult.SUCCESS);

        validateRequest(Action.SET_SETTING, requestArgumentCaptor.getValue(), setting);

        verify(restClient, times(2)).get(any());
    }

    private void testSimpleTorrentAction(Action action, Function<String, RequestResult> actionMethod) {
        when(restClient.get(any(Request.class))).thenReturn(TOKEN_VALUE);
        actionMethod.apply("");

        ArgumentCaptor<Request> requestArgumentCaptor = ArgumentCaptor.forClass(Request.class);
        when(restClient.get(requestArgumentCaptor.capture())).thenReturn(BUILD_STRING);
        RequestResult requestResult = actionMethod.apply(HASH_1);
        assertThat(requestResult).isEqualTo(RequestResult.SUCCESS);

        QueryParam expectedHash = new QueryParam(UTorrentWebAPIClientImpl.HASH_QUERY_PARAM_NAME, HASH_1);

        validateRequest(action, requestArgumentCaptor.getValue(), List.of(expectedHash));

        verify(restClient, times(2)).get(any()); //Once to retrieve the token, twice for the actual call
    }

    private void validateRequest(Action action, Request requestToValidate, List<QueryParam> queryParams) {
        validateRequest(action, requestToValidate, queryParams, List.of());
    }

    private void validateRequest(Action action, Request requestToValidate, List<QueryParam> queryParams, List<FilePart> fileList) {
        assertThat(requestToValidate).isNotNull();

        List<QueryParam> queryParamsToCompare = Stream.concat(
            Stream.of(
                new QueryParam(UTorrentWebAPIClientImpl.TOKEN_PARAM_NAME, TOKEN_VALUE),
                new QueryParam(UTorrentWebAPIClientImpl.ACTION_QUERY_PARAM_NAME, action.getName())
            ),
            queryParams.stream()
        ).collect(Collectors.toList());

        assertThat(requestToValidate.getUri()).isEqualTo(serverURI);
        assertThat(requestToValidate.getParams()).hasSameElementsAs(queryParamsToCompare);
        assertThat(requestToValidate.getFiles()).hasSameElementsAs(fileList);
    }

    public static final String TOKEN_VALUE = "token";
    public static final String HASH_1 = "hash_1";
    public static final String BUILD_STRING = "build";
}