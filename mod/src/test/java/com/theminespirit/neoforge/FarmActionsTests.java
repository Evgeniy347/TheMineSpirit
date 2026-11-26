package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FarmActionsTests {
    @Test
    void till_ValidSoil_UsesSharedBlockPlacement() throws IOException {
        String source = Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/FarmActions.java"));
        String compact = source.replaceAll("\\s+", "");

        assertFalse(
                source.contains("hoe.getItem().useOn(context)"),
                "Вспашка не должна вызывать HoeItem.useOn с UseOnContext без игрока: иначе ванильный контекст "
                        + "отказывает на dirt+air и wheat_field снова падает с «пашня не вышла». При смене "
                        + "till_ValidSoil_UsesSharedBlockPlacement проверь FarmActions.till и живой wheat_field.");
        assertTrue(
                compact.contains("SpiritBlocks.place((ServerLevel)level,ground,Blocks.FARMLAND.defaultBlockState()"),
                "Вспашка ставит farmland через общий SpiritBlocks.place: иначе изменение не попадёт в журнал "
                        + "и аудит мира снова увидит пустую клетку. При смене till_ValidSoil_UsesSharedBlockPlacement "
                        + "проверь BlockPlacer.farmland, SpiritBlocks.place и BuildGap.");
    }
}
