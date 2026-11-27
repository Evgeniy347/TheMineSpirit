package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.building.MineLayout;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BuildGapPlacedTests {
    private static final BlockCoord FARMLAND = new BlockCoord(1, 63, 1);
    private static final BlockCoord WATER = new BlockCoord(2, 63, 1);
    private static final BlockCoord WHEAT = new BlockCoord(3, 64, 1);

    @Test
    void trace_ActionAndFluidBlocks_MatchesWorldBlockNotItem() {
        Map<BlockCoord, String> world = Map.of(
                FARMLAND, "minecraft:farmland[moisture=0]",
                WATER, "minecraft:water[level=0]",
                WHEAT, "minecraft:wheat[age=7]");

        BuildGap.Gap gap = BuildGap.trace(plan(), "oak", new Probe(world), NoSchemes.INSTANCE);

        assertEquals(
                0,
                gap.shortage(),
                "Готовая пашня, вода и пшеница не должны считаться дырами: иначе аудит требует dirt, "
                        + "water_bucket и wheat_seeds вместо уже стоящих блоков и ремонт не закрывается"
                        + " При смене trace_ActionAndFluidBlocks_MatchesWorldBlockNotItem проверь BuildGap.expect.");
        assertEquals(
                Map.of("dirt", 1, "water_bucket", 1, "wheat_seeds", 1),
                gap.required(),
                "Недостача по-прежнему выражается в предметах: dirt-making farmland, ведро воды и семена "
                        + "нужны для настоящих пустых клеток"
                        + " Иначе тест trace_ActionAndFluidBlocks_MatchesWorldBlockNotItem перестанет ловить эту поломку. "
                        + "При смене trace_ActionAndFluidBlocks_MatchesWorldBlockNotItem проверь это утверждение.");
        assertEquals(
                Map.of(),
                gap.missingItems(),
                "У стоящих блоков нет недостачи предметов"
                        + " Иначе тест trace_ActionAndFluidBlocks_MatchesWorldBlockNotItem перестанет ловить эту поломку. "
                        + "При смене trace_ActionAndFluidBlocks_MatchesWorldBlockNotItem проверь это утверждение.");
    }

    @Test
    void trace_MissingActionAndFluid_ReportsRequiredItems() {
        BuildGap.Gap gap = BuildGap.trace(plan(), "oak", new Probe(Map.of()), NoSchemes.INSTANCE);

        assertEquals(
                Map.of("dirt", 1, "water_bucket", 1, "wheat_seeds", 1),
                gap.missingItems(),
                "Пустые клетки поля требуют именно предметы производства: иначе граф не подготовит землю, "
                        + "воду и семена до ремонта"
                        + " Иначе тест trace_MissingActionAndFluid_ReportsRequiredItems перестанет ловить эту поломку. "
                        + "При смене trace_MissingActionAndFluid_ReportsRequiredItems проверь BuildGap.count.");
    }

    private static TaskNode plan() {
        return new SequenceNode(List.of(
                place(FARMLAND, "minecraft:farmland"),
                place(WATER, "minecraft:water"),
                place(WHEAT, "minecraft:wheat")));
    }

    private static ScriptCall place(BlockCoord at, String block) {
        return new ScriptCall("place_block", Map.of("block", block, "x", at.x(), "y", at.y(), "z", at.z()));
    }

    private record Probe(Map<BlockCoord, String> world) implements BuildGap.Probe {
        @Override
        public String block(BlockCoord at) {
            return world.getOrDefault(at, "minecraft:air");
        }

        @Override
        public boolean passable(BlockCoord at) {
            return true;
        }
    }

    private enum NoSchemes implements BuildGap.Schemes {
        INSTANCE;

        @Override
        public Optional<MineLayout> mine(ScriptCall call) {
            return Optional.empty();
        }

        @Override
        public Optional<List<Schematic.PlacedEntry>> entries(ScriptCall call) {
            return Optional.empty();
        }
    }
}
