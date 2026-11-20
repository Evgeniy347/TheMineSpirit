package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.building.MineLayout;
import com.theminespirit.core.building.Preset;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.api.BuildRotation;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ConditionNode;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ParallelNode;
import com.theminespirit.core.plan.RepeatNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.reload.DataTables;
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

class MineAuditTests {
    @Test
    void torchInPassage_OutsideScheme_IsStray() {
        MineLayout layout = layout();
        BlockCoord stray = freeAir(layout);

        BuildGap.Gap gap =
                BuildGap.trace(minePlan(), "oak", probeWith(layout, Map.of(stray, "minecraft:torch")), schemes(layout));

        assertEquals(
                1,
                gap.shortage(),
                "Факел вне схемной клетки прохода — лишний блок: без этого аудит считает завал чистым, "
                        + "repair:build_mine не открывается, и дух месяцами ходит мимо мусора в стволе; "
                        + "при смене правила open проверь shortage=1 и строку «копать» в недостаче");
        assertTrue(
                gap.missing().stream().anyMatch(line -> line.contains("копать: minecraft:torch")),
                "Недостача обязана называть посторонний факел по id: иначе в живом логе [layout] видно только "
                        + "число, а не что именно выкапывать; при смене формата строки проверь «копать: <block>»");
    }

    @Test
    void schemeTorch_AtOptionalCell_CountsDone() {
        MineLayout layout = layout();
        Schematic.PlacedEntry torch = layout.optional().get(0);

        BuildGap.Gap gap = BuildGap.trace(
                minePlan(), "oak", probeWith(layout, Map.of(torch.pos(), torch.block())), schemes(layout));

        assertEquals(
                0,
                gap.shortage(),
                "Схемный факел в своей клетке — не завал: иначе каждый ремонт снимал бы исправный свет и "
                        + "требовал новые факелы; при смене сверки optional проверь shortage=0 и совпадение "
                        + "wall_torch с torch по предмету");
    }

    @Test
    void foreignBlock_AtOptionalCell_IsStray() {
        MineLayout layout = layout();
        Schematic.PlacedEntry torch = layout.optional().get(0);

        BuildGap.Gap gap = BuildGap.trace(
                minePlan(), "oak", probeWith(layout, Map.of(torch.pos(), "minecraft:dirt")), schemes(layout));

        assertEquals(
                1,
                gap.shortage(),
                "Чужой блок в клетке схемного факела — дефект: без этого MineFinish пропускает клетку, и свет "
                        + "по схеме там не появится никогда; при смене правила проверь shortage=1 и «копать: dirt»");
        assertTrue(
                gap.missing().stream().anyMatch(line -> line.contains("копать: minecraft:dirt")),
                "Недостача называет чужой блок, а не «воздух»: иначе ремонт получит задачу без понятной цели; "
                        + "при смене текста недостачи проверь id блока из мира, а не ожидаемый");
    }

    @Test
    void waterInPassage_StandsInsteadOfAir_IsStray() {
        MineLayout layout = layout();
        BlockCoord wet = freeAir(layout);

        BuildGap.Gap gap =
                BuildGap.trace(minePlan(), "oak", probeWith(layout, Map.of(wet, "minecraft:water")), schemes(layout));

        assertEquals(
                1,
                gap.shortage(),
                "Вода в клетке прохода не открывает проход: без этого дух идёт в затопленный ствол, а аудит "
                        + "молчит; при смене правила open проверь shortage=1 и строку «копать: water»");
        assertTrue(
                gap.missing().stream().anyMatch(line -> line.contains("копать: minecraft:water")),
                "Недостача обязана называть воду: иначе непонятно, что перекрывать булыжником; при смене "
                        + "формата строки проверь «копать: minecraft:water»");
    }

    @Test
    void minePlan_SchemeTorches_SuppliedBeforeBuild() {
        List<ScriptCall> calls = new ArrayList<>();
        collect(minePlan(), calls);
        int expected = layout().optional().size();
        assertTrue(
                expected > 0,
                "В схеме спуска нет клеток факелов: тест слеп и не проверит снабжение; при смене схемы "
                        + "проверь repeat.torch и legend «T», иначе MineFinish снова останется без света");
        ScriptCall craft = calls.stream()
                .filter(call -> "craft".equals(call.script())
                        && "minecraft:torch".equals(call.params().get("item")))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "В плане спуска нет крафта факелов: без этого MineFinish молча пропускает свет, "
                                + "ствол остаётся тёмным; при смене плана проверь TorchPlans.supply перед "
                                + "build_schematic"));
        assertEquals(
                expected,
                craft.params().get("count"),
                "Факелов крафтят ровно по числу схемных клеток: иначе часть ствола останется тёмной или "
                        + "перерасходуется уголь; при смене MineLayout.optional проверь count крафта");
        assertTrue(
                calls.indexOf(craft) < indexOfFirst(calls, "build_schematic"),
                "Факелы добываются до постройки: MineFinish ставит их из инвентаря в конце, без этого шага "
                        + "они не появятся; при смене порядка плана проверь индекс craft < build_schematic");
    }

    private static int indexOfFirst(List<ScriptCall> calls, String script) {
        for (int i = 0; i < calls.size(); i++) {
            if (script.equals(calls.get(i).script())) {
                return i;
            }
        }
        return -1;
    }

    private static void collect(TaskNode node, List<ScriptCall> out) {
        if (node instanceof SequenceNode sequence) {
            sequence.steps().forEach(step -> collect(step, out));
        } else if (node instanceof FallbackNode fallback) {
            fallback.steps().forEach(step -> collect(step, out));
        } else if (node instanceof RepeatNode repeat) {
            collect(repeat.step(), out);
        } else if (node instanceof ParallelNode parallel) {
            parallel.steps().forEach(step -> collect(step, out));
        } else if (node instanceof ConditionNode condition) {
            collect(condition.thenStep(), out);
            if (condition.elseStep() != null) {
                collect(condition.elseStep(), out);
            }
        } else if (node instanceof ScriptCall call) {
            out.add(call);
        }
    }

    private static BlockCoord freeAir(MineLayout layout) {
        Set<BlockCoord> torches = new HashSet<>();
        layout.optional().forEach(entry -> torches.add(entry.pos()));
        return layout.air().stream()
                .filter(cell -> !torches.contains(cell))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "В схеме спуска нет воздуха без факела: тест слеп; при смене схемы проверь, что у "
                                + "прохода есть обычные клетки без «T»"));
    }

    private static BuildGap.Probe probeWith(MineLayout layout, Map<BlockCoord, String> extras) {
        return new BuildGap.Probe() {
            @Override
            public String block(BlockCoord at) {
                String extra = extras.get(at);
                if (extra != null) {
                    return extra;
                }
                return layout.floors().contains(at) ? "minecraft:cobblestone" : "minecraft:air";
            }

            @Override
            public boolean passable(BlockCoord at) {
                String id = block(at);
                return "minecraft:air".equals(id) || id.endsWith("torch");
            }

            @Override
            public boolean open(BlockCoord at) {
                return "minecraft:air".equals(block(at));
            }
        };
    }

    private static MineLayout layout() {
        Preset preset = DataTables.INSTANCE
                .presets()
                .current()
                .byId(TicketPlans.MINE_DESCENT_SCHEME)
                .orElseThrow(() -> new AssertionError(
                        "Нет схемы спуска: тест не построит ни одной клетки; при смене presets проверь id "
                                + TicketPlans.MINE_DESCENT_SCHEME));
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
}
