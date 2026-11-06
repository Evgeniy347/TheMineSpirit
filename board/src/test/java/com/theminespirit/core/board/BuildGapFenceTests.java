package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.MineLayout;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.Ingredient;
import com.theminespirit.core.town.PlannedBlock;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StageWork;
import com.theminespirit.core.town.TownFixtures;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BuildGapFenceTests {
    private static final int FENCES = 6;
    private static final MaterialSource FAKE = new MaterialSource() {
        @Override
        public int outputCount(String item) {
            return item.endsWith("oak_fence") ? 3 : 1;
        }

        @Override
        public List<Ingredient> ingredientsFor(String item) {
            return item.endsWith("oak_fence") ? List.of(new Ingredient("oak_planks", 5)) : List.of();
        }

        @Override
        public Map<String, Integer> schematicBlocks(String file) {
            return Map.of();
        }
    };

    private static final BuildGap.Schemes NONE = new BuildGap.Schemes() {
        @Override
        public Optional<MineLayout> mine(ScriptCall call) {
            return Optional.empty();
        }

        @Override
        public Optional<List<Schematic.PlacedEntry>> entries(ScriptCall call) {
            return Optional.empty();
        }
    };

    @Test
    void need_WholeFencePlaced_EmptyEvenThoughPlanAsksMaterials() {
        TaskNode plan = fencePlan();
        BuildGap.Gap gap = BuildGap.trace(plan, "oak", probe(Set.of()), NONE);

        assertEquals(FENCES, gap.total(), "План ограды — все столбы сегмента: " + gap.total());
        assertEquals(0, gap.shortage(), "Все 26 блоков поставлены — недостачи нет");
        assertEquals(
                Map.of(),
                BuildGap.need(plan, gap, FAKE),
                "Если ограда поставлена целиком, задача не ждёт досок: расчёт «план целиком минус поставленное "
                        + "по имени блока» оставлял oak_planks и вешал first:build_fence навсегда");
    }

    @Test
    void need_PartialFence_AsksOnlyRemainingCraft() {
        List<BlockCoord> absent = positions().subList(0, 3);
        TaskNode plan = fencePlan();
        BuildGap.Gap gap = BuildGap.trace(plan, "oak", probe(new HashSet<>(absent)), NONE);

        assertEquals(3, gap.shortage(), "Три блока не поставлены — три и в недостаче: " + gap.missing());
        assertEquals(
                Map.of("oak_planks", 5),
                BuildGap.need(plan, gap, FAKE),
                "На три недостающих забора нужен один крафт (5 досок), а не материал всего сегмента (10)");
        assertTrue(
                gap.missing().stream().allMatch(line -> line.contains("oak_fence")),
                "Недостача ограды называет блок и место: " + gap.missing());
    }

    private static TaskNode fencePlan() {
        List<PlannedBlock> blocks = new ArrayList<>();
        for (BlockCoord at : positions()) {
            blocks.add(new PlannedBlock(at, "minecraft:oak_fence"));
        }
        StageSite site = TownFixtures.flatSite();
        StageWork work = new StageWork(site, blocks, List.of());
        TicketTarget target = TicketTarget.at(site.workYard()).withWork(work);
        return TicketPlans.plan(BoardCase.BUILD_FENCE, 1, target);
    }

    private static List<BlockCoord> positions() {
        BlockCoord yard = TownFixtures.flatSite().workYard();
        List<BlockCoord> positions = new ArrayList<>();
        for (int index = 0; index < FENCES; index++) {
            positions.add(new BlockCoord(yard.x() + index, yard.y(), yard.z()));
        }
        return List.copyOf(positions);
    }

    private static BuildGap.Probe probe(Set<BlockCoord> absent) {
        return new BuildGap.Probe() {
            @Override
            public String block(BlockCoord at) {
                return absent.contains(at) ? "minecraft:air" : "minecraft:oak_fence";
            }

            @Override
            public boolean passable(BlockCoord at) {
                return absent.contains(at);
            }
        };
    }
}
