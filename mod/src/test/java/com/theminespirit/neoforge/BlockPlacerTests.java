package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BlockPlacerTests {
    @Test
    void needsSoil_Stone_RequiresPreparation() {
        assertTrue(
                BlockPlacer.needsSoil("minecraft:stone"),
                "Камень в клетке пашни или тропы требует замены землёй: иначе wheat_field бесконечно получает "
                        + "отказ «пашня не вышла» и не сажает семена. При смене needsSoil_Stone_RequiresPreparation "
                        + "проверь BlockPlacer.prepareSoil и живое поле.");
    }

    @Test
    void needsSoil_ExistingSoilOrAir_DoesNotRequirePreparation() {
        assertFalse(
                BlockPlacer.needsSoil("minecraft:dirt"),
                "Земля уже пригодна для вспашки: лишняя замена тратила бы грунт и ломала уклон. Иначе поле "
                        + "теряет почву без необходимости. При смене needsSoil_ExistingSoilOrAir_DoesNotRequirePreparation "
                        + "проверь FarmActions.till.");
        assertFalse(
                BlockPlacer.needsSoil("minecraft:grass_block"),
                "Трава тоже пригодна для пашни и тропы: иначе грядка снова не сможет использовать мягкий грунт. "
                        + "При смене needsSoil_ExistingSoilOrAir_DoesNotRequirePreparation проверь PathTrample.");
        assertFalse(
                BlockPlacer.needsSoil("minecraft:air"),
                "Воздух готовит землю обычный путь схемы: предварительная замена воздуха не нужна. Иначе "
                        + "схема получит лишний расход грунта. При смене needsSoil_ExistingSoilOrAir_DoesNotRequirePreparation "
                        + "проверь BlockPlacer.laySoil.");
    }

    @Test
    void actionPlacement_NonSoilGround_UsesPreparationBeforeToolAction() throws IOException {
        String source = Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/BlockPlacer.java"));
        String compact = source.replaceAll("\\s+", "");
        int action = compact.indexOf("if(action){");
        int prepare = compact.indexOf("Beginsoil=prepareSoil");
        int farmland = compact.indexOf("?farmland(spirit,level,target)", action);

        assertTrue(
                action >= 0 && prepare > action && farmland > prepare,
                "Подготовка почвы выполняется до вспашки и топота: иначе камень остаётся в схеме, а семена не "
                        + "сажаются. При смене actionPlacement_NonSoilGround_UsesPreparationBeforeToolAction "
                        + "проверь BlockPlacer.begin и живой wheat_field.");
    }
}
