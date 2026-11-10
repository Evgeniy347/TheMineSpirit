package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class HangWatchGuardTests {
    private static final Path WATCH = Path.of("server/scripts/hang-watch.sh");
    private static final Path CHECK = Path.of("scripts/testing/check-hang-watch.sh");

    @Test
    void hangWatch_DeadConsole_IsRestarted() {
        String script = read(WATCH);
        assertTrue(
                script.contains("console_stream_alive") && script.contains("console_lost"),
                "hang-watch.sh не проверяет живой поток консоли: обновление JDK на месте (apt unattended) "
                        + "убивает stdin/stdout, REST и тик остаются живы, и сторож молчит, пока save-* и stop "
                        + "не подтверждаются. Как надо: jcmd VM.version + перезапуск");
        assertTrue(
                script.contains("VM.version"),
                "Проверка консоли обязана быть прикреплением jcmd к живой JVM (VM.version): один kill -0 "
                        + "показывает процесс, но не работающую консоль");
        assertTrue(
                script.contains("нет jcmd") || script.contains("command -v \"$JCMD\""),
                "Без jcmd (JRE вместо JDK) сторож не судит о консоли: иначе каждый прогон на JRE перезапускал "
                        + "бы здоровый сервер");
        String check = read(CHECK);
        for (String scenario : List.of("консоль мертва", "нет-jcmd", "console-")) {
            assertTrue(
                    check.contains(scenario),
                    "Проверка check-hang-watch.sh потеряла опыт «" + scenario + "»: мёртвая консоль, отсутствие "
                            + "jcmd и файл дампа не должны расходиться с исправлением");
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new UncheckedIOException("Не прочитан " + path, error);
        }
    }
}
