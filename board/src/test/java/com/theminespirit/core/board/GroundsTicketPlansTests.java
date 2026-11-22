package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.api.CatalogLimits;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.PlotLeveling;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GroundsTicketPlansTests {
    @Test
    void plan_ClearGrounds_StartsAtFeetLevelAndKeepsGround() {
        BlockCoord home = new BlockCoord(10, 60, -5);

        List<Integer> bottoms =
                collectCalls(TicketPlans.plan(BoardCase.CLEAR_GROUNDS, TicketTarget.at(home)), "clear_area").stream()
                        .flatMap(call -> boxes(call).stream())
                        .map(box -> Integer.parseInt(box.split(",", -1)[1]))
                        .distinct()
                        .toList();

        assertEquals(
                List.of(home.y()),
                bottoms,
                "Дом — клетка ног: слой y−1 — земля участка; расчистка с y−1 обходила 33×33 блоков земли по одному "
                        + "и не кончалась до конца дня (приёмка «джунгли»), иначе задание висит вторые сутки. "
                        + "При смене plan_ClearGrounds_StartsAtFeetLevelAndKeepsGround проверь нижний слой ящиков в списке boxes.");
    }

    @Test
    void plan_ClearGrounds_EachCallFitsVolumeLimitAndCoversWholeSite() {
        BlockCoord center = new BlockCoord(10, 60, -5);
        TaskNode plan = TicketPlans.plan(BoardCase.CLEAR_GROUNDS, TicketTarget.at(center));
        List<ScriptCall> calls = collectCalls(plan, "clear_area");
        Set<String> cells = new HashSet<>();
        List<ScriptCall> boxed = new ArrayList<>();
        for (ScriptCall call : calls) {
            List<String> boxes = boxes(call);
            if (boxes.isEmpty()) {
                continue;
            }
            boxed.add(call);
            for (String box : boxes) {
                String[] parts = box.split(",", -1);
                int x1 = Integer.parseInt(parts[0]);
                int y1 = Integer.parseInt(parts[1]);
                int z1 = Integer.parseInt(parts[2]);
                int x2 = Integer.parseInt(parts[3]);
                int y2 = Integer.parseInt(parts[4]);
                int z2 = Integer.parseInt(parts[5]);
                long volume = (long) (x2 - x1 + 1) * (y2 - y1 + 1) * (z2 - z1 + 1);
                assertTrue(
                        volume <= CatalogLimits.BUILD_VOLUME_MAX,
                        "Каждый ящик перечня в пределах clear_area " + CatalogLimits.BUILD_VOLUME_MAX + ": скрипт "
                                + "разворачивает список сам, но ящик сверх предела — отказ и вихрь провалов, "
                                + "иначе дух не начнёт расчистку. При смене "
                                + "plan_ClearGrounds_EachCallFitsVolumeLimitAndCoversWholeSite проверь лимит на ящик.");
                for (int x = x1; x <= x2; x++) {
                    for (int y = y1; y <= y2; y++) {
                        for (int z = z1; z <= z2; z++) {
                            cells.add(x + ":" + y + ":" + z);
                        }
                    }
                }
            }
        }

        assertEquals(
                1,
                boxed.size(),
                "Один вызов расчистки со всем перечнем ящиков (решение 22.11): дух берёт задание целиком, "
                        + "а дробление на части осталось в прошлом, иначе части снова разойдутся по разным духам. "
                        + "При смене plan_ClearGrounds_EachCallFitsVolumeLimitAndCoversWholeSite проверь число вызовов.");
        assertEquals(
                33 * 33 * 4,
                cells.size(),
                "Ящики перечня обязаны покрыть весь участок радиуса 16 без дыр: иначе факты поселения снова увидят "
                        + "деревья и переоткроют кейс, а расчистка закончится с остатками. При смене "
                        + "plan_ClearGrounds_EachCallFitsVolumeLimitAndCoversWholeSite проверь полноту перечня.");
    }

    @Test
    void plan_ClearQuarter_OnlyItsLevelingBoxes() {
        BlockCoord anchor = new BlockCoord(0, 64, 0);
        PlotLeveling leveling = new PlotLeveling(
                List.of(
                        new PlotLeveling.Box(-12, 64, -12, -10, 65, -12),
                        new PlotLeveling.Box(8, 64, -12, 10, 65, -12)),
                List.of(new PlotLeveling.Box(3, 60, 4, 4, 63, 4)),
                "dirt");
        TicketTarget quarter = TicketTarget.at(anchor).withLeveling(leveling).withCount(3);
        List<ScriptCall> calls = calls(TicketPlans.plan(BoardCase.CLEAR_GROUNDS, quarter));
        List<ScriptCall> cuts =
                calls.stream().filter(call -> !boxes(call).isEmpty()).toList();
        assertEquals(
                1,
                cuts.size(),
                "Один вызов срезки на весь участок без деления на части: иначе выступы уезжают разным духам "
                        + "и выравнивание договориться не может. При смене plan_ClearQuarter_OnlyItsLevelingBoxes "
                        + "проверь, что перечень срезки один.");
        List<String> cutBoxes = boxes(cuts.get(0));
        assertTrue(
                cutBoxes.stream().anyMatch(box -> box.startsWith("-12,64,")),
                "Срезка западного выступа входит в перечень задания: иначе холм у края участка остаётся, "
                        + "выравнивание переоткрывается. При смене plan_ClearQuarter_OnlyItsLevelingBoxes "
                        + "проверь западный ящик в списке.");
        assertTrue(
                cutBoxes.stream().anyMatch(box -> box.startsWith("8,64,")),
                "Срезка восточного выступа входит в перечень задания: иначе холм у края участка остаётся, "
                        + "выравнивание переоткрывается. При смене plan_ClearQuarter_OnlyItsLevelingBoxes "
                        + "проверь восточный ящик в списке.");
        assertTrue(
                calls.stream().anyMatch(call -> "fill".equals(call.script())),
                "Подсыпка ямы остаётся отдельным шагом задания: иначе яма у края участка не засыпается, "
                        + "и дух падает в неё на следующем круге. При смене plan_ClearQuarter_OnlyItsLevelingBoxes "
                        + "проверь шаг fill для void-ящика.");
    }

    @Test
    void plan_ClearWithFillAndCut_NoClearBelowLevelDigsGround() {
        BlockCoord anchor = new BlockCoord(0, 70, 0);
        PlotLeveling leveling = PlotLeveling.plan(anchor, square(anchor, 4), (x, z) -> x < 0 ? 64 : x > 2 ? 73 : 70);
        TicketTarget whole = TicketTarget.at(anchor).withLeveling(leveling);
        List<ScriptCall> clears = calls(TicketPlans.plan(BoardCase.CLEAR_GROUNDS, whole)).stream()
                .filter(call -> "clear_area".equals(call.script()))
                .toList();
        assertTrue(
                clears.stream().anyMatch(call -> !boxes(call).isEmpty()),
                "Срезка идёт списком ящиков: иначе проверка ниже ничего не доказывает и краснеет вхолостую, "
                        + "При смене plan_ClearWithFillAndCut_NoClearBelowLevelDigsGround проверь, "
                        + "что срезка приходит списком boxes.");
        for (ScriptCall clear : clears) {
            List<String> boxes = boxes(clear);
            if (!boxes.isEmpty()) {
                assertTrue(
                        !Boolean.TRUE.equals(clear.params().get("keep_ground")),
                        "Срезка выше уровня землю снимает без keep_ground: иначе земля участка остаётся под "
                                + "кустами и выравнивание не сходится. При смене "
                                + "plan_ClearWithFillAndCut_NoClearBelowLevelDigsGround проверь keep_ground "
                                + "у вызова срезки: " + clear.params());
                for (String box : boxes) {
                    int y1 = Integer.parseInt(box.split(",", -1)[1]);
                    assertTrue(
                            y1 >= anchor.y(),
                            "Срезка не идёт ниже уровня участка " + anchor.y() + ": ямы подсыпки — отдельный шаг "
                                    + "с keep_ground, иначе дух роет яму посреди участка и падает в неё (INC-77). "
                                    + "При смене plan_ClearWithFillAndCut_NoClearBelowLevelDigsGround проверь "
                                    + "нижние границы ящиков срезки.");
                }
            } else if (clear.params().containsKey("y1")) {
                boolean below = ((Integer) clear.params().get("y1")) < anchor.y();
                assertEquals(
                        below,
                        Boolean.TRUE.equals(clear.params().get("keep_ground")),
                        "Подсыпка ниже уровня участка — только с keep_ground, иначе дух роет яму посреди "
                                + "участка и падает в неё (INC-77). При смене "
                                + "plan_ClearWithFillAndCut_NoClearBelowLevelDigsGround проверь keep_ground "
                                + "у подсыпки: " + clear.params());
            }
        }
    }

    private static List<String> boxes(ScriptCall call) {
        Object value = call.params().get("boxes");
        return value instanceof List<?> list
                ? list.stream().map(String.class::cast).toList()
                : List.of();
    }

    private static List<ScriptCall> calls(TaskNode root) {
        List<ScriptCall> found = new ArrayList<>();
        collect(root, found);
        return found;
    }

    private static void collect(TaskNode node, List<ScriptCall> found) {
        if (node instanceof ScriptCall call) {
            found.add(call);
        } else if (node instanceof SequenceNode sequence) {
            sequence.steps().forEach(step -> collect(step, found));
        } else if (node instanceof FallbackNode fallback) {
            fallback.steps().forEach(step -> collect(step, found));
        }
    }

    private static List<ScriptCall> collectCalls(TaskNode root, String script) {
        List<ScriptCall> calls = new ArrayList<>();
        Deque<TaskNode> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            TaskNode node = queue.poll();
            switch (node) {
                case ScriptCall call -> {
                    if (call.script().equals(script)) {
                        calls.add(call);
                    }
                }
                case SequenceNode sequence -> queue.addAll(sequence.steps());
                case FallbackNode fallback -> queue.addAll(fallback.steps());
                default -> {}
            }
        }
        return calls;
    }

    private static ColumnMask square(BlockCoord center, int half) {
        return ColumnMask.rect(center.x() - half, center.z() - half, center.x() + half, center.z() + half);
    }
}
