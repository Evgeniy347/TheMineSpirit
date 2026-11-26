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
        int nearest = compact.indexOf("StandSearch.nearest(", guard);
        int cancel = compact.indexOf("host.approach.cancel();", guard);
        int approach = compact.indexOf("host.beginApproach(surface)", guard);
        int planning = compact.indexOf("host.ticks++;", guard);

        assertTrue(
                guard >= 0 && nearest > guard && cancel > nearest && approach > cancel && planning > approach,
                "Пашня под землёй сначала возвращает духа на поверхность: иначе стройка снова начинается "
                        + "внутри farmland и не доходит до края. При смене tick_TilledSchemeBelowSurface_ReturnsBeforePlanning "
                        + "проверь SchematicWorkPhase.tick и живой wheat_field.");
        assertTrue(
                compact.contains("host.approachStand==null||host.approachStand.y()<host.site.origin().y()"),
                "Подход к подземной стоянке отменяется, а подход к поверхности сохраняется: иначе старый "
                        + "маршрут снова уводит духа под грядку. При смене "
                        + "tick_TilledSchemeBelowSurface_ReturnsBeforePlanning проверь approachStand.");
        assertTrue(
                compact.contains("cell.y()>=host.site.origin().y()&&host.site.standable(cell)")
                        && compact.contains("surface.y()>=host.site.origin().y()&&host.site.standable(surface)"),
                "StandSearch не может вернуть клетку ниже пашни или центр без опоры: иначе дух снова "
                        + "оказывается на y=63 и проваливает схему. При смене "
                        + "tick_TilledSchemeBelowSurface_ReturnsBeforePlanning проверь фильтр поверхности.");
    }
}
