package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SnapshotVersionGuardTests {
    private static final Path SNAPSHOT = Path.of("server/scripts/world-snapshot.sh");
    private static final Path PUBLISH = Path.of("scripts/publish.sh");
    private static final Path LIVE = Path.of("scripts/lead/live.py");
    private static final Path VERSION = Path.of("scripts/mod-version.sh");
    private static final Path MOD_VERSION = Path.of("scripts/mod_version.py");
    private static final Path SNAPSHOT_CHECK = Path.of("scripts/testing/check-world-snapshot.sh");
    private static final Path VERSION_CHECK = Path.of("scripts/testing/check-mod-version.sh");

    @Test
    void worldSnapshot_WaitsForConsoleAcks_AndNamesTheCause() {
        String script = read(SNAPSHOT);
        for (String ack :
                List.of("Automatic saving is now disabled", "Saved the game", "Automatic saving is now enabled")) {
            assertTrue(
                    script.contains(ack),
                    "world-snapshot.sh не ждёт подтверждения «" + ack + "»: 10.11 поток консоли умер после "
                            + "обновления JDK, save-off остался включённым, а снимок сутки писал «сервер не подтвердил "
                            + "сохранение мира» без причины. Как надо: wait_for_ack на каждую команду save-*");
        }
        assertTrue(
                script.contains("консоль не читает stdin") && script.contains("не пишет лог"),
                "world-snapshot.sh обязан различать «сервер жив, консоль мертва» и «сервер завис»: без этого "
                        + "дежурный не знает, перезапускать сервер или смотреть hang-watch.sh");
        assertTrue(
                script.contains("flock -w") && script.contains("exit 75"),
                "world-snapshot.sh не ждёт идущий снимок и не называет занятый замок: публикация падала с "
                        + "«уже идёт другой снимок» и кодом 0 у самого снимка (10.11, PUBLISH_EXIT=2)");
        assertTrue(
                Files.isRegularFile(SNAPSHOT_CHECK),
                "Нет scripts/testing/check-world-snapshot.sh: нечем проверить, что мёртвая консоль не оставляет "
                        + "мир без автосохранения");
        String check = read(SNAPSHOT_CHECK);
        for (String scenario : List.of(
                "запущенный сервер",
                "остановленный сервер",
                "мёртвая консоль",
                "save-all молчит",
                "save-on молчит",
                "занятый замок")) {
            assertTrue(
                    check.contains(scenario),
                    "Проверка check-world-snapshot.sh потеряла опыт «" + scenario + "»: подтверждения save-*, "
                            + "возврат save-on, занятый замок и успешный архив не должны расходиться");
        }
    }

    @Test
    void publish_SnapshotBusy_WaitsForIt() {
        String script = read(PUBLISH);
        assertTrue(
                script.contains("SNAPSHOT_LOCK_WAIT"),
                "publish.sh не передаёт снимку ожидание замка: снимок по cron каждые 10 минут занимал замок, и "
                        + "выкладка падала, хотя могла подождать пару минут");
    }

    @Test
    void liveCheck_ModVersion_MustMatchBuilt() {
        String script = read(LIVE);
        assertTrue(
                script.contains("mod_version.py") && script.contains("running_mod_version"),
                "live.sh check не спрашивает, какую сборку загрузил сервер: «ОК» 10.11 стояло при моде 0.1.3454 "
                        + "и новой сборке в mods/");
        assertTrue(
                script.contains("installed_mod_version") && script.contains("expected_mod_version"),
                "live.sh check не сравнивает загруженную версию с mods/ и с собранной из дерева: без этого "
                        + "проверка после выкладки пропускает невступившую в силу сборку");
        assertTrue(
                script.contains("--version") && script.contains("args.version"),
                "live.sh check обязан давать явное ожидание версии (--version): иначе с дерева задачи он сравнивает "
                        + "сервер с чужим main и пугает ложной ошибкой");
        assertTrue(
                Files.isRegularFile(MOD_VERSION),
                "Нет scripts/mod_version.py: чтение версии из debug.log было бы третьей копией разбора в живых "
                        + "скриптах и тестах");
        String version = read(MOD_VERSION);
        assertTrue(
                version.contains("Found valid mod file theminespirit-") && version.contains("DEBUG_SEARCH_LINES"),
                "mod_version.py обязан брать последнюю запись NeoForge о загруженном mod-файле: иначе после "
                        + "перезапуска вернётся старая сборка");
        assertFalse(
                script.contains("\"bash\"") || script.contains("'bash'"),
                "live.py не зовёт bash за версией копии: на машине, где bash = WSL, пути Windows ему чужие "
                        + "и проверка версии падает. Как надо: читать debug.log тем же mod_version.py на python");
        assertTrue(
                Files.isRegularFile(VERSION_CHECK),
                "Нет scripts/testing/check-mod-version.sh: разбор журнала NeoForge не проверен на живых строках");
        String check = read(VERSION_CHECK);
        for (String scenario : List.of("0.1.3454", "последняя загрузка", "не выдумывается", "собранная версия")) {
            assertTrue(
                    check.contains(scenario),
                    "Проверка check-mod-version.sh потеряла опыт «" + scenario + "»: версия из журнала и из дерева "
                            + "обязана оставаться проверенной");
        }
    }

    @Test
    void modVersionScript_AfterRestart_ReadsLastLoad() {
        String wrapper = read(VERSION);
        assertTrue(
                wrapper.contains("mod_version.py") && wrapper.contains("python_bin"),
                "mod-version.sh обязан делегировать разбор одному mod_version.py через scripts/python-bin.sh: "
                        + "копия разбора в bash расходится с живым live.py и требует bash там, где он WSL");
        String script = read(MOD_VERSION);
        assertTrue(
                script.contains("Found valid mod file theminespirit-") && script.contains("found = line"),
                "mod_version.py обязан брать последнюю запись NeoForge о загруженном mod-файле: иначе после "
                        + "перезапуска вернётся старая сборка");
        List<String> problems = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(MOD_VERSION, StandardCharsets.UTF_8)) {
                if (!line.trim().startsWith("#") && (line.contains("mods/") || line.contains("latest.log"))) {
                    problems.add(line);
                }
            }
        } catch (IOException error) {
            throw new UncheckedIOException("Не прочитан " + MOD_VERSION, error);
        }
        assertEquals(
                List.of(),
                problems,
                "mod_version.py не угадывает версию по mods/ или latest.log: имя jar доказывает установку, а не "
                        + "загрузку (10.11: сервер крутил 0.1.3454 при новой сборке на диске)");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new UncheckedIOException("Не прочитан " + path, error);
        }
    }
}
