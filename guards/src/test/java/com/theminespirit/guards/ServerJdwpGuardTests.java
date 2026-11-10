package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ServerJdwpGuardTests {
    private static final Path JVM_ARGS = Path.of("server/user_jvm_args.txt");
    private static final Path RUN = Path.of("server/run.sh");

    @Test
    void serverJdwp_OffByDefault_EnabledByEnvironment() {
        String jvm = read(JVM_ARGS);
        assertFalse(
                jvm.contains("-agentlib:jdwp"),
                "Основной сервер не держит JDWP на *:5005 всегда: отладочный порт в локальной сети открыт "
                        + "постоянно и подозревался в зависании консоли (debtmisc7). Как надо: без строки jdwp "
                        + "в user_jvm_args.txt, включать переменной окружения");
        String run = read(RUN);
        assertTrue(
                run.contains("MINESPIRIT_JDWP_PORT") && run.contains("agentlib:jdwp"),
                "run.sh обязан включать JDWP только по MINESPIRIT_JDWP_PORT: иначе вернуть отладку можно "
                        + "лишь ручной правкой user_jvm_args.txt, которую publish.sh перезапишет");
        assertTrue(
                run.contains("127.0.0.1:"),
                "JDWP основного сервера слушает 127.0.0.1, а не *: отладочный порт не выставляется в сеть");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new UncheckedIOException("Сторож не прочитал " + path + ": " + error.getMessage(), error);
        }
    }
}
