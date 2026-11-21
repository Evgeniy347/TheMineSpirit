package com.theminespirit.neoforge.rest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RestDefaultsTests {
    @Test
    void files_DefaultPaths_AreRelative() {
        assertFalse(
                RestDefaults.HEARTBEAT_FILE.startsWith("/"),
                "Умолчание heartbeatFile снова абсолютное (" + RestDefaults.HEARTBEAT_FILE + "): на чужой машине "
                        + "и в dev-копии этот путь бессмысленен, а сторож зависания молча не находит файл. "
                        + "Серверный путь задаётся в server/config/theminespirit-rest.toml"
                        + " Иначе тест files_DefaultPaths_AreRelative перестанет ловить эту поломку. При смене files_DefaultPaths_AreRelative проверь это утверждение.");
        assertFalse(
                RestDefaults.DEBUG_LEASE_FILE.startsWith("/"),
                "Умолчание debugLeaseFile снова абсолютное (" + RestDefaults.DEBUG_LEASE_FILE + "): замок живой "
                        + "отладки не должен зависеть от раскладки /opt, его путь задаёт конфиг сервера"
                        + " Иначе тест files_DefaultPaths_AreRelative перестанет ловить эту поломку. При смене files_DefaultPaths_AreRelative проверь это утверждение.");
    }

    @Test
    void serverConfig_PublishesPaths_OverridesDefaults() throws IOException {
        String toml = Files.readString(Path.of("server/config/theminespirit-rest.toml"), StandardCharsets.UTF_8);
        assertTrue(
                toml.contains("heartbeatFile = \"/opt/minecraft/logs/tick.heartbeat\""),
                "Опубликованный конфиг сервера обязан явно задать heartbeatFile: иначе серверный сторож зависания "
                        + "получит относительное умолчание и перестанет видеть тик"
                        + " При смене serverConfig_PublishesPaths_OverridesDefaults проверь это утверждение.");
        assertTrue(
                toml.contains("debugLeaseFile = \"/opt/minecraft/debug.lease\""),
                "Опубликованный конфиг сервера обязан явно задать debugLeaseFile: иначе замок отладки уедет "
                        + "в рабочий каталог сервера"
                        + " При смене serverConfig_PublishesPaths_OverridesDefaults проверь это утверждение.");
    }
}
