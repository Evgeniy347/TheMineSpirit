package com.theminespirit.core.tactics;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

final class TacticsResources {
    private static final String ROOT = "/data/theminespirit/tactics/";
    private static final AtomicInteger READS = new AtomicInteger();

    private TacticsResources() {}

    static int reads() {
        return READS.get();
    }

    static String read(String name) {
        READS.incrementAndGet();
        try (InputStream stream = TacticsResources.class.getResourceAsStream(ROOT + name)) {
            if (stream == null) {
                throw new IllegalStateException("Ресурс тактики " + ROOT + name
                        + " не найден в classpath: проверьте, что файл лежит в src/main/resources и имя без опечаток");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Ресурс тактики " + name + " не прочитан: проверьте права и кодировку UTF-8", e);
        }
    }
}
