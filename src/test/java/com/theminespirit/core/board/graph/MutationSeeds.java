package com.theminespirit.core.board.graph;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;

final class MutationSeeds {
    private static final String PROPERTY = "theminespirit.mutation.seed";
    private static final String ENV = "MUTATION_SEED";
    private static final Path LOG = Path.of("TestResults/mutation/seed.txt");
    private static final long SEED = resolve();

    static {
        log();
    }

    private MutationSeeds() {}

    static long seed() {
        return SEED;
    }

    static String replay() {
        return "./gradlew mutationTest -Dtheminespirit.mutation.seed=" + SEED;
    }

    private static long resolve() {
        String property = System.getProperty(PROPERTY);
        if (property != null && !property.isBlank()) {
            return Long.parseLong(property.trim());
        }
        String environment = System.getenv(ENV);
        if (environment != null && !environment.isBlank()) {
            return Long.parseLong(environment.trim());
        }
        return LocalDate.now(ZoneOffset.UTC).toEpochDay() * 7919L + 17L;
    }

    private static void log() {
        String line = "mutation seed " + String.format(Locale.ROOT, "%d", SEED) + "; повторить: " + replay()
                + System.lineSeparator();
        System.out.print(line);
        try {
            Files.createDirectories(LOG.getParent());
            Files.writeString(LOG, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException failure) {
            throw new IllegalStateException("Не записать сид мутаций в " + LOG + ": " + failure.getMessage(), failure);
        }
    }
}
