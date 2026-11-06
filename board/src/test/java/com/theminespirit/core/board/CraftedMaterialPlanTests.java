package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class CraftedMaterialPlanTests {
    private static final BlockCoord SITE = new BlockCoord(-179, 68, -515);
    private static final BlockCoord CHEST = new BlockCoord(-181, 68, -524);
    private static final Set<String> WORLD_SCRIPTS = Set.of("gather", "chop_tree", "mine_vein", "strip_log");
    private static final Set<String> CRAFTED = Set.of(
            "oak_planks", "minecraft:oak_planks", "oak_fence", "oak_door", "torch", "chest", "oak_sapling", "glass");

    @Test
    void gatherMaterials_CraftedItem_PlanNeverGathersOwnBlock() {
        for (String item : CRAFTED) {
            TaskNode plan = TicketPlans.plan(
                    BoardCase.GATHER_MATERIALS, TicketTarget.at(SITE).withSupply(item, 12));

            List<String> own = worldGathers(plan, item);

            assertTrue(
                    own.isEmpty(),
                    "graph:make/supply_gather для '" + item + "' снова ищет его блоком в мире: " + own
                            + ". Крафтовый предмет добывается рецептом, а не размещёнными копиями");
        }
    }

    @Test
    void supplySite_CraftedItem_PlanNeverGathersOwnBlock() {
        for (String item : CRAFTED) {
            TaskNode plan = TicketPlans.plan(
                    BoardCase.SUPPLY_SITE,
                    TicketTarget.at(SITE).withSupply(item, 12).withDeposit(CHEST));

            List<String> own = worldGathers(plan, item);

            assertTrue(
                    own.isEmpty(),
                    "supply:'" + item + "' снова ищет его блоком в мире: " + own
                            + ". Крафтовый предмет делается рецептом, снабжение только несёт его на склад");
        }
    }

    @Test
    void stoneAxe_MissingStone_PlanMinesStoneBeforeCraft() {
        TaskNode plan = TicketPlans.plan(
                BoardCase.CRAFT_STONE_AXE, TicketTarget.at(SITE).withToolCount(1));

        List<ScriptCall> calls = calls(plan);

        assertTrue(
                calls.stream().anyMatch(call -> "acquire".equals(call.script()) && stone(call)),
                "Крафт каменного топора без камня блокируется вместо добычи: в плане нет шага acquire камня. "
                        + "Как надо: камень добывается (шахта/поверхность), затем топор крафтится. План: " + calls);
        int acquire = index(calls, call -> "acquire".equals(call.script()) && stone(call));
        int craft = index(calls, call -> "craft".equals(call.script()));
        assertTrue(
                acquire >= 0 && craft > acquire,
                "Камень обязан добываться до крафта топора: иначе CraftScript падает «не хватает cobblestone». "
                        + "Шаги: " + calls);
    }

    @Test
    void gatherMaterials_StrippedLog_PlansAxeBeforeStripping() {
        TaskNode plan = TicketPlans.plan(
                BoardCase.GATHER_MATERIALS, TicketTarget.at(SITE).withSupply("stripped_oak_log", 4));

        List<ScriptCall> calls = calls(plan);
        int axe = index(calls, call -> "acquire".equals(call.script()) && axe(call));
        int strip = index(calls, call -> "strip_log".equals(call.script()));

        assertTrue(
                axe >= 0 && strip > axe,
                "Обтёсывание брёвен без топора падает: «нет топора для обтёсывания брёвен породы oak» "
                        + "(observe9, F). Как надо: сначала acquire топора, потом strip_log. Шаги: " + calls);
    }

    private static boolean axe(ScriptCall call) {
        Object item = call.params().get("item");
        return item instanceof String id && id.endsWith("_axe");
    }

    private static boolean stone(ScriptCall call) {
        Object item = call.params().get("item");
        return item instanceof String id
                && ("cobblestone".equals(id)
                        || "minecraft:cobblestone".equals(id)
                        || "cobbled_deepslate".equals(id)
                        || "blackstone".equals(id));
    }

    private static int index(List<ScriptCall> calls, Predicate<ScriptCall> match) {
        for (int i = 0; i < calls.size(); i++) {
            if (match.test(calls.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static List<String> worldGathers(TaskNode plan, String item) {
        String bare = item.startsWith("minecraft:") ? item.substring("minecraft:".length()) : item;
        List<String> found = new ArrayList<>();
        for (ScriptCall call : calls(plan)) {
            if (!WORLD_SCRIPTS.contains(call.script())) {
                continue;
            }
            Object block = call.params().get("block");
            Object ore = call.params().get("ore");
            Object subject = block == null ? ore : block;
            if (subject instanceof String id && bare(id).equals(bare)) {
                found.add(call.script() + "(" + subject + ")");
            }
        }
        return found;
    }

    private static String bare(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    private static List<ScriptCall> calls(TaskNode node) {
        List<ScriptCall> calls = new ArrayList<>();
        collect(node, calls);
        return calls;
    }

    private static void collect(TaskNode node, List<ScriptCall> calls) {
        if (node instanceof ScriptCall call) {
            calls.add(call);
            return;
        }
        if (node instanceof SequenceNode sequence) {
            sequence.steps().forEach(step -> collect(step, calls));
            return;
        }
        if (node instanceof FallbackNode fallback) {
            fallback.steps().forEach(step -> collect(step, calls));
        }
    }

    @Test
    void gatherMaterials_CraftTarget_MakesTheItemInsteadOfDirt() {
        TaskNode plan = TicketPlans.plan(
                BoardCase.GATHER_MATERIALS, TicketTarget.at(SITE).withCraft("wooden_hoe", 1));

        List<String> crafted = calls(plan).stream()
                .filter(call -> "craft".equals(call.script()))
                .map(call -> String.valueOf(call.params().get("item")))
                .toList();

        assertTrue(
                crafted.contains("wooden_hoe"),
                "Задание графа craft_wooden_hoe@дух обязано крафтить hoe, а не добывать dirt: " + crafted);
        assertTrue(
                calls(plan).stream()
                        .noneMatch(call -> "dirt".equals(call.params().get("item"))),
                "Цель крафта не подменяется предметом снабжения по умолчанию: иначе дух весь прогон таскает землю");
    }

    @Test
    void gatherMaterials_SupplyTarget_StillGathersItem() {
        TaskNode plan = TicketPlans.plan(
                BoardCase.GATHER_MATERIALS, TicketTarget.at(SITE).withSupply("oak_log", 4));

        assertTrue(
                calls(plan).stream()
                        .anyMatch(call -> "oak_log".equals(call.params().get("item"))
                                || "tree".equals(call.params().get("target"))),
                "Обычное снабжение не сломано: цель без craftItem по-прежнему добывает свой предмет");
    }
}
