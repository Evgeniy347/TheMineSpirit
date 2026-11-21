package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.api.BuildRotation;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.repair.RepairData;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RepairAuditTests {
    private static final BlockCoord ORIGIN = new BlockCoord(10, 64, 10);
    private static final String AIR = "minecraft:air";

    @Test
    void audit_MissingBlock_PlacesBySchemeAndRepairsToIt() {
        Map<BlockCoord, String> world = new HashMap<>();
        world.put(ORIGIN, AIR);
        world.put(new BlockCoord(11, 64, 10), "minecraft:oak_planks");
        world.put(new BlockCoord(12, 64, 10), "minecraft:cobblestone");
        RepairAudit.Report report = repairAudit(scheme(), world, Set.of(), Map.of(), 100);
        assertEquals(
                3,
                report.total(),
                "В схеме три обязательных блока: по ним считается доля повреждения Иначе тест audit_MissingBlock_PlacesBySchemeAndRepairsToIt перестанет ловить эту поломку. При смене audit_MissingBlock_PlacesBySchemeAndRepairsToIt проверь это утверждение.");
        assertEquals(
                1,
                report.missing(),
                "Нет одного блока из трёх: воздух вместо булыжника Иначе тест audit_MissingBlock_PlacesBySchemeAndRepairsToIt перестанет ловить эту поломку. При смене audit_MissingBlock_PlacesBySchemeAndRepairsToIt проверь это утверждение.");
        assertFalse(
                report.rebuild(),
                "Одного блока из трёх мало для перестройки: порог из repair.json выше Иначе тест audit_MissingBlock_PlacesBySchemeAndRepairsToIt перестанет ловить эту поломку. При смене audit_MissingBlock_PlacesBySchemeAndRepairsToIt проверь это утверждение.");
        assertEquals(
                List.of(new RepairAudit.Fix(ORIGIN, RepairAudit.Action.PLACE, "minecraft:cobblestone", AIR, 0)),
                report.fixes(),
                "Аудит обязан вернуть клетку и блок схемы: без этого ремонт не знает, что ставить При смене audit_MissingBlock_PlacesBySchemeAndRepairsToIt проверь это утверждение.");
        for (RepairAudit.Fix fix : report.fixes()) {
            world.put(fix.cell(), fix.wanted());
        }
        assertEquals(
                List.of(),
                repairAudit(scheme(), world, Set.of(), Map.of(), 100).fixes(),
                "Ремонт возвращает постройку к схеме: после правок мир совпадает со схемой Иначе тест audit_MissingBlock_PlacesBySchemeAndRepairsToIt перестанет ловить эту поломку. При смене audit_MissingBlock_PlacesBySchemeAndRepairsToIt проверь это утверждение.");
    }

    private static RepairAudit.Report repairAudit(
            Schematic scheme,
            Map<BlockCoord, String> world,
            Set<BlockCoord> player,
            Map<BlockCoord, Long> firstSeen,
            long now) {
        return RepairAudit.audit(
                scheme, ORIGIN, BuildRotation.NONE, new MapWorld(world, player), RepairData.defaults(), firstSeen, now);
    }

    private static Schematic scheme() {
        return new Schematic(
                3,
                1,
                1,
                List.of(
                        new Schematic.Entry(0, 0, 0, "cobblestone"),
                        new Schematic.Entry(1, 0, 0, "oak_planks"),
                        new Schematic.Entry(2, 0, 0, "cobblestone")));
    }

    private record MapWorld(Map<BlockCoord, String> blocks, Set<BlockCoord> player) implements RepairAudit.WorldView {
        @Override
        public String blockAt(BlockCoord cell) {
            return blocks.getOrDefault(cell, AIR);
        }

        @Override
        public boolean playerPlaced(BlockCoord cell) {
            return player.contains(cell);
        }
    }
}
