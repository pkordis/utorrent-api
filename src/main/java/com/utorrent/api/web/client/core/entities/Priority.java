package com.utorrent.api.web.client.core.entities;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Priority{
    DO_NOT_DOWNLOAD(0), 
    LOW_PRIORITY(1),
    NORMAL_PRIORITY(2),
    HIGH_PRIORITY(3);

    private final int value;

    public static Priority getPriority(final int value) {
        for (final Priority priority : Priority.values()) {
            if (priority.getValue() == value) {
                return priority;
            }
        }
        return null;
    }
}
