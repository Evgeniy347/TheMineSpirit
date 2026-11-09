package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BuildGap;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.MineLayout;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StageWork;
import com.theminespirit.core.town.TownFixtures;
import com.theminespirit.neoforge.SchematicResources;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OrchardReadinessTests {
    private static final String SCHEME = "orchard_1";
    private static final String SAPLING = "oak_sapling";
    private static final BuildGap.Schemes SCHEMES = new BuildGap.Schemes() {
        @Override
        public Optional<MineLayout> mine(ScriptCall call) {
            return SchemeAudit.mine(call);
        }

        @Override
        public Optional<List<Schematic.PlacedEntry>> entries(ScriptCall call) {
            return SchemeAudit.entries(call);
        }
    };

    @Test
    void trace_OrchardWithSaplings_IsComplete() {
        assertEquals(28, gap(standing(true)).total(), "сад считает 20 дорожек, 4 факела и 4 саженца");
        assertEquals(0, gap(standing(true)).shortage(), "сад с посаженными саженцами готов на 100%");
    }

    @Test
    void trace_OrchardWithGrownTrees_IsComplete() {
        assertEquals(
                0,
                gap(standing(false)).shortage(),
                "выросшие деревья того же вида заменяют саженцы: закрытие этапа не ждёт роста дерева");
    }

    @Test
    void trace_EmptyOrchardCells_NeedSaplings() {
        Map<BlockCoord, String> empty = standing(true);
        empty.replaceAll((cell, block) -> block.endsWith("_sapling") ? "minecraft:air" : block);
        BuildGap.Gap gap = gap(empty);

        assertEquals(4, gap.shortage(), "пустая клетка сада — недостроенный сад: нужен саженец");
        assertEquals(
                Map.of(SAPLING, 4),
                gap.missingItems(),
                "недостача сада называет саженцы: по ней ставится задача посадки");
    }

    private static BuildGap.Gap gap(Map<BlockCoord, String> world) {
        StageSite site = TownFixtures.flatSite();
        TicketTarget target = TicketTarget.at(site.workYard()).withWork(new StageWork(site, List.of()));
        TaskNode plan = TicketPlans.plan(BoardCase.BUILD_ORCHARD, 1, target);
        return BuildGap.trace(plan, "oak", probe(world), SCHEMES);
    }

    private static Map<BlockCoord, String> standing(boolean saplings) {
        StageSite site = TownFixtures.flatSite();
        BlockCoord origin = site.orchardOrigin();
        Map<BlockCoord, String> world = new LinkedHashMap<>();
        for (Schematic.PlacedEntry entry : SchematicResources.load(SCHEME).entries(origin, BuildRotation.NONE)) {
            boolean required =
                    !entry.optional() || SchematicBlocks.path(entry.block()).endsWith("_sapling");
            if (!required || SchematicBlocks.isSecondaryPart(entry.block(), entry.state())) {
                continue;
            }
            String block = SchematicBlocks.path(entry.block());
            if (block.endsWith("_sapling") && !saplings) {
                block = block.substring(0, block.length() - "_sapling".length()) + "_log";
            }
            world.put(entry.pos(), "minecraft:" + block);
        }
        return world;
    }

    private static BuildGap.Probe probe(Map<BlockCoord, String> world) {
        return new BuildGap.Probe() {
            @Override
            public String block(BlockCoord at) {
                return world.getOrDefault(at, "minecraft:air");
            }

            @Override
            public boolean passable(BlockCoord at) {
                return "minecraft:air".equals(block(at));
            }
        };
    }
}
