package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.MineLayout;
import com.theminespirit.core.building.Preset;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.Ingredient;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StageWork;
import com.theminespirit.core.town.TownFixtures;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BuildGapMineTests {
    private static final String LIVE_BOARD = "/live/observe12-minereopen/board.json";
    private static final MaterialSource FAKE = new MaterialSource() {
        @Override
        public int outputCount(String item) {
            return 1;
        }

        @Override
        public List<Ingredient> ingredientsFor(String item) {
            return List.of();
        }

        @Override
        public Map<String, Integer> schematicBlocks(String file) {
            return Map.of();
        }
    };

    @Test
    void trace_LiveMineGap_NamesEveryMissingFloorAndNeedsCobblestone() throws IOException {
        Board live = LiveBoardFixture.load(LIVE_BOARD);
        BoardTicket mine = live.find("first:build_mine")
                .orElseThrow(() -> new AssertionError("В живой доске нет first:build_mine: тест без неё слеп"));
        int missingFloors = mine.requiredItems().getOrDefault("cobblestone", 0);
        assertTrue(
                missingFloors > 0,
                "Живая доска observe12 требует cobblestone=" + missingFloors
                        + " на восстановление шахты: без непустого требования тест не проверяет дефект");

        MineLayout layout = layout();
        assertTrue(
                layout.floors().size() > missingFloors,
                "Пол шахты (" + layout.floors().size() + ") обязан быть больше недостачи (" + missingFloors
                        + "), иначе нечего проверять");

        Set<BlockCoord> absent = new HashSet<>(layout.floors().subList(0, missingFloors));
        TaskNode plan = minePlan();
        BuildGap.Gap gap = BuildGap.trace(plan, "oak", probe(layout, absent), schemes(layout));

        assertEquals(
                layout.floors().size() + layout.air().size(),
                gap.total(),
                "Готовность шахты — воздух проходки и пол вместе: иначе граф, план и доска снова считают по-разному");
        assertEquals(
                missingFloors,
                gap.shortage(),
                "Недостача шахты считается по блокам мира, а не по журналу: живая доска видит те же " + missingFloors
                        + " блоков, что и аудит");
        assertEquals(
                Map.of("cobblestone", missingFloors),
                gap.missingItems(),
                "Недостающие полы шахты — это cobblestone: столько и должно уйти в requiredItems восстановления");
        assertEquals(
                Map.of("cobblestone", missingFloors),
                BuildGap.need(plan, gap, FAKE),
                "Ремонт шахты обязан требовать ровно недостающие полы: пустой список материалов превращал "
                        + "repair:build_mine в «план 0 блоков»");
        assertFalse(
                gap.missing().isEmpty(),
                "Недостача шахты обязана называть блоки: [layout] печатал «недостающие []» при 57 пропавших");
        assertTrue(
                gap.missing().stream().allMatch(line -> line.contains("cobblestone")),
                "Каждая строка недостачи шахты называет блок пола: " + gap.missing());
    }

    private static MineLayout layout() {
        Preset preset = DataTables.INSTANCE
                .presets()
                .current()
                .byId(TicketPlans.MINE_DESCENT_SCHEME)
                .orElseThrow();
        return MineLayout.place(
                preset.schematic(),
                preset.entry(),
                site().mineEntrance(),
                BuildRotation.parse(TicketPlans.MINE_DESCENT_ROTATION),
                MineLayout.Extent.NONE);
    }

    private static TaskNode minePlan() {
        return TicketPlans.plan(
                BoardCase.BUILD_MINE,
                1,
                TicketTarget.at(site().mineEntrance()).withWork(new StageWork(site(), List.of())));
    }

    private static StageSite site() {
        return TownFixtures.flatSite();
    }

    private static BuildGap.Schemes schemes(MineLayout layout) {
        return new BuildGap.Schemes() {
            @Override
            public Optional<MineLayout> mine(ScriptCall call) {
                return "build_schematic".equals(call.script()) ? Optional.of(layout) : Optional.empty();
            }

            @Override
            public Optional<List<Schematic.PlacedEntry>> entries(ScriptCall call) {
                return Optional.empty();
            }
        };
    }

    private static BuildGap.Probe probe(MineLayout layout, Set<BlockCoord> absent) {
        return new BuildGap.Probe() {
            @Override
            public String block(BlockCoord at) {
                if (layout.floors().contains(at)) {
                    return absent.contains(at) ? "minecraft:air" : "minecraft:cobblestone";
                }
                return "minecraft:air";
            }

            @Override
            public boolean passable(BlockCoord at) {
                return !layout.floors().contains(at) || absent.contains(at);
            }
        };
    }

    @Test
    void trace_CompleteMine_ShortageZero() {
        MineLayout layout = layout();
        BuildGap.Gap gap = BuildGap.trace(minePlan(), "oak", probe(layout, Set.of()), schemes(layout));

        assertEquals(0, gap.shortage(), "Достроенная шахта не имеет недостачи");
        assertEquals(
                Map.of(),
                BuildGap.need(minePlan(), gap, FAKE),
                "Достроенной шахте не нужны материалы: пустая недостача не тянет ремонт");
    }

    @Test
    void trace_OreInDescentWithoutPickaxe_CountsAsDone() {
        MineLayout layout = layout();
        BlockCoord ore = layout.air().get(layout.air().size() / 2);

        BuildGap.Gap gap = BuildGap.trace(minePlan(), "oak", probeWithOre(layout, ore), schemes(layout));

        assertEquals(
                layout.floors().size() + layout.air().size(),
                gap.done(),
                "Клетка руды в проходке считается выполненной: задание left_ore ждёт кирку, а постройка идёт в обход");
        assertEquals(0, gap.shortage(), "Руда в спуске не должна держать готовность шахты ниже 100%");
        assertEquals(
                Map.of(), BuildGap.need(minePlan(), gap, FAKE), "Руде не нужен булыжник: ремонт шахты её не требует");
    }

    @Test
    void trace_OreInWallAndFloor_CountsAsDone() {
        MineLayout layout = layout();
        BlockCoord floorOre = layout.floors().get(0);
        BlockCoord wallOre = layout.floors().get(layout.floors().size() - 1);

        BuildGap.Gap gap = BuildGap.trace(minePlan(), "oak", probeWithOre(layout, floorOre, wallOre), schemes(layout));

        assertEquals(0, gap.shortage(), "Руда в полу и стенке спуска считается выполненной клеткой: 100% без кирки");
    }

    private static BuildGap.Probe probeWithOre(MineLayout layout, BlockCoord... ores) {
        Set<BlockCoord> oreCells = Set.of(ores);
        return new BuildGap.Probe() {
            @Override
            public String block(BlockCoord at) {
                if (oreCells.contains(at)) {
                    return "minecraft:diamond_ore";
                }
                if (layout.floors().contains(at)) {
                    return "minecraft:cobblestone";
                }
                return "minecraft:air";
            }

            @Override
            public boolean passable(BlockCoord at) {
                return !layout.floors().contains(at) && !oreCells.contains(at);
            }
        };
    }
}
