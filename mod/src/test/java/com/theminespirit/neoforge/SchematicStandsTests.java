package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SchematicStandsTests {
    @Test
    void moveTowards_WalkNetworkHasNoStand_FallsBackToDirectStands() throws IOException {
        String source = Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/SchematicStands.java"));
        String compact = source.replaceAll("\\s+", "");
        int fallback = compact.indexOf("if(stand.isEmpty()&&walkKnown){");
        int direct = compact.indexOf("choose(host,cells,stands,excluded,occupied,outsideOnly,reaches)", fallback);
        int blocked = compact.indexOf("host.blocked.putIfAbsent", fallback);

        assertTrue(
                fallback >= 0 && direct > fallback && blocked > direct,
                "Если предварительная сеть ходьбы не содержит стоянку, строитель пробует прямые стоянки до "
                        + "блокировки клетки: иначе wheat_field остаётся с недостижимым левым краем. При смене "
                        + "moveTowards_WalkNetworkHasNoStand_FallsBackToDirectStands проверь SchematicStands.moveTowards "
                        + "и живое поле.");
    }

    @Test
    void schematicStanding_FenceFloor_IsRejected() throws IOException {
        String source =
                Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/SchematicStanding.java"));
        String compact = source.replaceAll("\\s+", "");

        assertTrue(
                compact.contains("feet.y()-1") && compact.contains("!FenceWatch.isFence("),
                "Стойка на заборе обязана быть запрещена: иначе строитель выбирает стоянку на перилах, путь"
                        + " обрывается у преграды и перенос зависает. При смене стоянок перепроверь"
                        + " SchematicStanding.standable и BoardV2GameTests.relocate_PenMoved_OldClearedNewStands.");
    }
}
