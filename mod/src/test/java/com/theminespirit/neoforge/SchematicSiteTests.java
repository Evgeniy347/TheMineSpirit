package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.observation.BlockCoord;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchematicSiteTests {
    @Test
    void standBase_TilledScheme_UsesWalkingSurface() {
        Schematic field = new Schematic(1, 1, 1, List.of(new Schematic.Entry(0, 0, 0, "minecraft:farmland")));

        assertEquals(
                64,
                SchematicSite.standBase(field, new BlockCoord(0, 64, 0)),
                "Пашня не заставляет духа спускаться под грунт: иначе он оказывается внутри farmland, не "
                        + "доходит до края и проваливает wheat_field. При смене standBase_TilledScheme_UsesWalkingSurface "
                        + "проверь SchematicStands и живое поле.");
    }

    @Test
    void matches_GrowingSoftCrop_IgnoresAgeState() throws IOException {
        String source = Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/SchematicSite.java"));
        String compact = source.replaceAll("\\s+", "");

        assertTrue(
                compact.contains("booleangrowingCrop=entry.optional()&&SchematicActions.isCrop(entry.block())")
                        && compact.contains(
                                "returnblock&&(growingCrop||sameSpecifiedState(current,wanted,entry.state()))")
                        && compact.contains(
                                "if(entry.optional()&&SchematicActions.isCrop(entry.block())){returnfalse;}"),
                "Мягкий выросший посев не сбрасывается в age=0: иначе ремонт поля тормозит урожай и "
                        + "семена расходуются впустую. При смене matches_GrowingSoftCrop_IgnoresAgeState "
                        + "проверь SchematicSite.matches, rightBlockWrongState и живой wheat_field.");
    }

    @Test
    void standBase_OtherGroundScheme_KeepsExistingLowerBound() {
        Schematic ground = new Schematic(1, 1, 1, List.of(new Schematic.Entry(0, 0, 0, "minecraft:dirt")));

        assertEquals(
                62,
                SchematicSite.standBase(ground, new BlockCoord(0, 64, 0)),
                "Обычные грунтовые схемы сохраняют прежний допуск стоянок: иначе снос и ремонт земли "
                        + "перестанут работать в низинах. При смене standBase_OtherGroundScheme_KeepsExistingLowerBound "
                        + "проверь BuildStandLevel.standBase.");
    }
}
