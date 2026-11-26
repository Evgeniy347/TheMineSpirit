package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.observation.BlockCoord;
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
