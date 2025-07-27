package com.utorrent.api.web.client.core;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
enum Action {
    START("start"),
    STOP("stop"),
    PAUSE("pause"),
    FORCE_START("forcestart"),
    UN_PAUSE("unpause"),
    RECHECK("recheck"),
    REMOVE("remove"),
    REMOVE_DATA("removedata"),
    SET_PRIORITY("setprio"),
    GET_PROP("getprops"),
    ADD_URL("add-url"),
    GET_FILES("getfiles"),
    GET_SETTINGS("getsettings"),
    SET_SETTING("setsetting"),
    ADD_FILE("add-file"),
    QUEUE_BOTTOM("queuebottom"),
    QUEUE_DOWN("queuedown"),
    QUEUE_TOP("queuetop"),
    QUEUE_UP("queueup");

    private final String name;
}
