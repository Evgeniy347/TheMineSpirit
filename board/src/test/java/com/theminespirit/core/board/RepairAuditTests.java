package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        RepairAudit.Report report = repairAudit(scheme(), world, Set.of());
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
                List.of(new RepairAudit.Fix(ORIGIN, RepairAudit.Action.PLACE, "minecraft:cobblestone", AIR)),
                report.fixes(),
                "Аудит обязан вернуть клетку и блок схемы: без этого ремонт не знает, что ставить При смене audit_MissingBlock_PlacesBySchemeAndRepairsToIt проверь это утверждение.");
        for (RepairAudit.Fix fix : report.fixes()) {
            world.put(fix.cell(), fix.wanted());
        }
        assertEquals(
                List.of(),
                repairAudit(scheme(), world, Set.of()).fixes(),
                "Ремонт возвращает постройку к схеме: после правок мир совпадает со схемой Иначе тест audit_MissingBlock_PlacesBySchemeAndRepairsToIt перестанет ловить эту поломку. При смене audit_MissingBlock_PlacesBySchemeAndRepairsToIt проверь это утверждение.");
    }

    @Test
    void audit_EmptyWheatCell_RequestsSchematicResowingAndGrownWheatIsKept() {
        Schematic field = new Schematic(
                3,
                1,
                1,
                List.of(
                        new Schematic.Entry(0, 0, 0, "wheat[age=0]"),
                        new Schematic.Entry(1, 0, 0, "dirt"),
                        new Schematic.Entry(2, 0, 0, "dirt")));
        Map<BlockCoord, String> world = new HashMap<>();
        world.put(ORIGIN, AIR);
        world.put(new BlockCoord(11, 64, 10), "minecraft:dirt");
        world.put(new BlockCoord(12, 64, 10), "minecraft:dirt");

        RepairAudit.Report empty = RepairAudit.audit(
                field, ORIGIN, BuildRotation.NONE, new MapWorld(world, Set.of()), RepairData.defaults());

        assertEquals(
                3,
                empty.total(),
                "Схема содержит три клетки поля: иначе аудит не проверяет посев. При смене "
                        + "audit_EmptyWheatCell_RequestsSchematicResowingAndGrownWheatIsKept проверь Schematic.entries.");
        assertEquals(
                List.of(new RepairAudit.Fix(ORIGIN, RepairAudit.Action.PLACE, "minecraft:wheat[age=0]", AIR)),
                empty.fixes(),
                "Пустая клетка пшеницы — повреждение схемы: иначе после урожая поле не засеется. При смене "
                        + "audit_EmptyWheatCell_RequestsSchematicResowingAndGrownWheatIsKept проверь repair.json "
                        + "и StagePlans.placed.");
        world.put(ORIGIN, "minecraft:wheat[age=7]");
        RepairAudit.Report grown = RepairAudit.audit(
                field, ORIGIN, BuildRotation.NONE, new MapWorld(world, Set.of()), RepairData.defaults());
        assertTrue(
                grown.fixes().isEmpty(),
                "Выросший колос принимается любой степени зрелости: иначе ремонт будет снимать и пересаживать "
                        + "урожай каждый аудит. При смене audit_EmptyWheatCell_RequestsSchematicResowingAndGrownWheatIsKept "
                        + "проверь BlockMatch.matches.");
    }

    private static RepairAudit.Report repairAudit(
            Schematic scheme, Map<BlockCoord, String> world, Set<BlockCoord> player) {
        return RepairAudit.audit(
                scheme, ORIGIN, BuildRotation.NONE, new MapWorld(world, player), RepairData.defaults());
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
    }
}
