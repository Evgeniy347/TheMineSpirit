package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SchematicSurfaceTests {
    @Test
    void tick_TilledSchemeBelowSurface_ReturnsBeforePlanning() throws IOException {
        String source =
                Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/SchematicWorkPhase.java"));
        String compact = source.replaceAll("\\s+", "");
        int guard = compact.indexOf("SchematicGround.tilled(host.site.schematic())");
        int nearest = compact.indexOf("StandSearch.nearest(top,host.site::standable)", guard);
        int approach = compact.indexOf("host.beginApproach(surface)", guard);
        int planning = compact.indexOf("host.ticks++;", guard);

        assertTrue(
                guard >= 0 && nearest > guard && approach > nearest && planning > approach,
                "Пашня под землёй сначала возвращает духа на поверхность: иначе стройка снова начинается "
                        + "внутри farmland и не доходит до края. При смене tick_TilledSchemeBelowSurface_ReturnsBeforePlanning "
                        + "проверь SchematicWorkPhase.tick и живой wheat_field.");
        assertTrue(
                compact.contains("!host.approach.isActive()&&host.feet().y()<host.site.origin().y()"),
                "Уже начатый подход к поверхности не перезапускается каждый тик: иначе дух никогда не "
                        + "сдвинется с места. При смене tick_TilledSchemeBelowSurface_ReturnsBeforePlanning "
                        + "проверь защиту approach.isActive.");
    }
}
