package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.SchemeBuild;
import com.theminespirit.core.town.StageSchemes;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StageWork;
import com.theminespirit.core.town.TownFixtures;
import com.theminespirit.core.town.WallLayout;
import com.theminespirit.core.town.YardScheme;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SchemeBuildGuardTests {
    private static final String CALL = "new PlannedBlock(";
    private static final List<String> EXCEPTIONS = List.of("SUPPORT", "sapling");
    private static final Set<String> DEMOLITION = Set.of("PlanDemolition.java", "ChestDemolition.java");
    private static final String HOW =
            "Правило владельца: все постройки доски (BUILD_*, LIGHTING, STOCK_CHESTS, ограда, "
                    + "улицы) — только стройкой по текстовой схеме (SchemeBuild → build_schematic). Цена поблочного списка: "
                    + "постройку не видно в каталоге, её не проверяют сторожа схем, а ремонт и аудит расходятся со схемой. "
                    + "Как надо: заведите схему и ставьте её клетки; поблочно можно только землю ремонта ровности "
                    + "(StageBuilds.SUPPORT) и саженец посадки.";

    @Test
    void plannedBlocks_ProductionSources_OnlyGroundAndSapling() {
        List<String> found = new ArrayList<>();
        for (Map.Entry<Path, String> file : SourceFiles.allMain().entrySet()) {
            found.addAll(violations(file.getKey().getFileName().toString(), file.getValue()));
        }
        assertEquals(List.of(), found, HOW);
    }

    @Test
    void violations_FenceBlock_Reported() {
        assertEquals(
                1,
                violations("Any.java", "x(new PlannedBlock(at, \"minecraft:oak_fence\"));")
                        .size(),
                "сторож обязан поймать поблочный забор: иначе правило «только по схеме» не держится");
        assertEquals(
                List.of(),
                violations("Any.java", "x(new PlannedBlock(new BlockCoord(1, 2, 3), SUPPORT));"),
                "земля ремонта ровности — названное исключение сторожа");
        assertEquals(
                List.of(),
                violations("PlanDemolition.java", "x(new PlannedBlock(cell, StagePlans.AIR));"),
                "снос лишнего — не постройка: воздух сноса сторож не трогает");
        assertEquals(
                List.of(),
                violations("ChestDemolition.java", "x(new PlannedBlock(cell, \"minecraft:air\"));"),
                "снос сундука — разбор, а не постройка: воздух сноса сторож не трогает");
    }

    @Test
    void stagePlans_BuildCases_PlaceOnlyGroundBlocks() {
        StageSite site = TownFixtures.flatSite();
        List<SchemeBuild> fence = WallLayout.of(DataTables.INSTANCE.presets().current(), StageSchemes.FENCE)
                .closed(site.ring().posts(), site.ring().gates())
                .stream()
                .map(piece -> new SchemeBuild(
                        piece.scheme(), new BlockCoord(piece.x(), site.anchor().y(), piece.z()), piece.rotation()))
                .toList();
        Map<BoardCase, List<SchemeBuild>> works = Map.of(
                BoardCase.BUILD_FENCE, fence,
                BoardCase.BUILD_HOUSE, List.of(),
                BoardCase.BUILD_ORCHARD, List.of(),
                BoardCase.STOCK_CHESTS, YardScheme.stock(site, 1),
                BoardCase.CRAFT_FURNACE, List.of(YardScheme.furnace(site)),
                BoardCase.LIGHTING, YardScheme.lighting(site));
        works.forEach((step, builds) -> {
            TaskNode plan = TicketPlans.plan(
                    step, 1, TicketTarget.at(site.workYard()).withWork(new StageWork(site, List.of(), builds)));
            List<ScriptCall> calls = new ArrayList<>();
            collect(plan, calls);
            assertTrue(
                    calls.stream().anyMatch(call -> "build_schematic".equals(call.script())),
                    step + " строится стройкой по схеме. " + HOW);
            assertEquals(
                    List.of(),
                    calls.stream()
                            .filter(call -> "place_block".equals(call.script()))
                            .map(call -> call.params().get("block"))
                            .toList(),
                    step + " не ставит блоков постройки поблочно. " + HOW);
        });
    }

    static List<String> violations(String file, String text) {
        List<String> found = new ArrayList<>();
        int at = text.indexOf(CALL);
        while (at >= 0) {
            String call = text.substring(at, closing(text, at + CALL.length()));
            boolean allowed = EXCEPTIONS.stream().anyMatch(call::contains)
                    || (DEMOLITION.contains(file)
                            && (call.contains("StagePlans.AIR") || call.contains("\"minecraft:air\"")));
            if (!allowed) {
                found.add(file + ":" + SourceFiles.lineOf(text, at) + " " + call);
            }
            at = text.indexOf(CALL, at + CALL.length());
        }
        return found;
    }

    private static int closing(String text, int from) {
        int depth = 1;
        for (int index = from; index < text.length(); index++) {
            char c = text.charAt(index);
            depth += c == '(' ? 1 : c == ')' ? -1 : 0;
            if (depth == 0) {
                return index + 1;
            }
        }
        return text.length();
    }

    private static void collect(TaskNode node, List<ScriptCall> calls) {
        switch (node) {
            case ScriptCall call -> calls.add(call);
            case SequenceNode sequence -> sequence.steps().forEach(step -> collect(step, calls));
            case FallbackNode fallback -> collect(fallback.steps().get(0), calls);
            default -> {}
        }
    }
}
