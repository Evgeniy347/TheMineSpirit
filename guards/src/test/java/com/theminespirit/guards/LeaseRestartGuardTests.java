package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LeaseRestartGuardTests {
    private static final Path LIFECYCLE = Path.of("com/theminespirit/neoforge/lease/LeaseLifecycle.java");

    @Test
    void sources_StartDoesNotOrphanLeases_NoViolations() {
        assertEquals(List.of(), violations(SourceFiles.allMain()), "Рестарт сервера снова бросает аренды");
    }

    @Test
    void patternSource_StartOrphansAllActive_IsReported() {
        String bad =
                "final class LeaseLifecycle { void onStarted(ServerLevel level) {" + " data.orphanAllActive(); } }";

        assertEquals(
                List.of("LeaseLifecycle.java: старт сервера снова бросает активные аренды"),
                violations(Map.of(LIFECYCLE, bad)),
                "Сторож ослеп: возврат orphanAllActive при старте вернёт undo-шторм после каждой публикации");
    }

    @Test
    void patternSource_DeathOrphansBySpirit_IsAccepted() {
        String good = "final class LeaseLifecycle { void onDeath() { data.orphanSpirit(id); } }";

        assertEquals(
                List.of(),
                violations(Map.of(LIFECYCLE, good)),
                "Сторож ложно срабатывает: смерть духа — законная причина бросить его аренды");
    }

    private static List<String> violations(Map<Path, String> sources) {
        List<String> found = new ArrayList<>();
        sources.forEach((path, text) -> {
            if (!"LeaseLifecycle.java".equals(path.getFileName().toString())) {
                return;
            }
            if (SourceFiles.withoutTextBlocks(text).contains("orphanAllActive()")) {
                found.add("LeaseLifecycle.java: старт сервера снова бросает активные аренды");
            }
        });
        return found;
    }
}
