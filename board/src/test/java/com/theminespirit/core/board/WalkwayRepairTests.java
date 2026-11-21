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
import org.junit.jupiter.api.Test;

class WalkwayRepairTests {
    private static final BlockCoord ORIGIN = new BlockCoord(10, 64, 10);
    private static final String AIR = "minecraft:air";

    @Test
    void audit_SunkWalkway_LiftsWithoutRebuild() {
        Schematic scheme = new Schematic(1, 1, 1, List.of(new Schematic.Entry(0, 0, 0, "dirt_path")));
        BlockCoord plan = plan();
        Map<BlockCoord, String> world = new HashMap<>();
        world.put(new BlockCoord(plan.x(), plan.y() - 1, plan.z()), "minecraft:dirt_path");

        RepairAudit.Report report = repairAudit(scheme, world);

        assertFalse(
                report.rebuild(),
                "Перекос тропы — не разрушение: утопленную тропу поднимают в клетку плана, а не перестраивают"
                        + " Иначе тест audit_SunkWalkway_LiftsWithoutRebuild перестанет ловить эту поломку. При смене audit_SunkWalkway_LiftsWithoutRebuild проверь это утверждение.");
        assertEquals(
                0,
                report.missing(),
                "Утопленная тропа под клеткой плана — работа подъёма, а не потерянная клетка схемы"
                        + " Иначе тест audit_SunkWalkway_LiftsWithoutRebuild перестанет ловить эту поломку. При смене audit_SunkWalkway_LiftsWithoutRebuild проверь это утверждение.");
        assertTrue(
                report.damaged(),
                "Подъём обязан попасть в правки ремонта: иначе тропа останется ступенькой, отчёт " + report
                        + " При смене audit_SunkWalkway_LiftsWithoutRebuild проверь это утверждение.");
        assertEquals(
                List.of(new RepairAudit.Fix(plan, RepairAudit.Action.PLACE, "minecraft:dirt_path", AIR, 0)),
                report.fixes(),
                "Ремонт получает клетку тропы и её блок: подъём исполняет та же постановка дорожки"
                        + " Иначе тест audit_SunkWalkway_LiftsWithoutRebuild перестанет ловить эту поломку. При смене audit_SunkWalkway_LiftsWithoutRebuild проверь это утверждение.");
    }

    @Test
    void audit_MissingWalkwayWithoutOldPath_Rebuilds() {
        Schematic scheme = new Schematic(1, 1, 1, List.of(new Schematic.Entry(0, 0, 0, "dirt_path")));
        BlockCoord plan = plan();
        Map<BlockCoord, String> world = new HashMap<>();
        world.put(new BlockCoord(plan.x(), plan.y() - 1, plan.z()), "minecraft:stone");

        RepairAudit.Report report = repairAudit(scheme, world);

        assertEquals(
                1,
                report.missing(),
                "Тропы нет и старой тропы под клеткой нет — клетка потеряна"
                        + " Иначе тест audit_MissingWalkwayWithoutOldPath_Rebuilds перестанет ловить эту поломку. При смене audit_MissingWalkwayWithoutOldPath_Rebuilds проверь это утверждение.");
        assertTrue(
                report.rebuild(),
                "Потеря всей схемы — перестройка, а не подъём утопленной тропы"
                        + " Иначе тест audit_MissingWalkwayWithoutOldPath_Rebuilds перестанет ловить эту поломку. При смене audit_MissingWalkwayWithoutOldPath_Rebuilds проверь это утверждение.");
    }

    private static BlockCoord plan() {
        return new BlockCoord(ORIGIN.x(), ORIGIN.y() - 1, ORIGIN.z());
    }

    private static RepairAudit.Report repairAudit(Schematic scheme, Map<BlockCoord, String> world) {
        return RepairAudit.audit(
                scheme, ORIGIN, BuildRotation.NONE, new MapWorld(world), RepairData.defaults(), Map.of(), 100);
    }

    private record MapWorld(Map<BlockCoord, String> blocks) implements RepairAudit.WorldView {
        @Override
        public String blockAt(BlockCoord cell) {
            return blocks.getOrDefault(cell, AIR);
        }

        @Override
        public boolean playerPlaced(BlockCoord cell) {
            return false;
        }
    }
}
