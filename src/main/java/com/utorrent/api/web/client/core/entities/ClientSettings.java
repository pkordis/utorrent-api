package com.utorrent.api.web.client.core.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@ToString
public class ClientSettings {
    private final Map<String, Setting> settingsMap = new HashMap<>();

    public void addSetting(
        final String name,
        final int typeCode,
        final String value
    ) {
        settingsMap.put(name, new Setting(value, SettingType.getTypeFromCode(typeCode)));
    }

    public Setting getSetting(final String name) {
        return settingsMap.get(name);
    }

    public Collection<Setting> getAllSettings() {
        return settingsMap.values();
    }

    @Getter
    @AllArgsConstructor
    public static class Setting {
        private final String value;
        private final SettingType type;
    }

    @Getter
    @RequiredArgsConstructor
    public enum SettingType {
        INTEGER(0),
        BOOLEAN(1),
        STRING(2);

        private final int code;

        public static SettingType getTypeFromCode(int code) {
            for (final SettingType type : SettingType.values()) {
                if (type.getCode() == code) {
                    return type;
                }
            }
            return null;
        }
    }
}
