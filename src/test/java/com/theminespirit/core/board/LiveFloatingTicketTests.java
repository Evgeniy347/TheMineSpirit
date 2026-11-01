package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.town.PlannedBlock;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StageWork;
import com.theminespirit.core.town.TownFixtures;
import com.theminespirit.live.LiveFloatingWorld;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class LiveFloatingTicketTests {
    private static final BlockCoord GROUND = new BlockCoord(-163, 69, -507);

    @Test
    void liveFloatingCrownTicket_Plan_BuildsSupportAndChopsWholeTree() throws IOException {
        JsonNode fixture = LiveFloatingWorld.load().root();
        List<PlannedBlock> blocks = crownBlocks(fixture);
        int top = blocks.stream().mapToInt(block -> block.at().y()).max().orElseThrow();
        BlockCoord highest = new BlockCoord(-163, top, -507);

        StageSite site = TownFixtures.flatSite();
        TicketTarget target =
                TicketTarget.at(GROUND).withWork(new StageWork(site, blocks)).withRelocate(GROUND, highest);
        TaskNode plan = StagePlans.plan(BoardCase.DISMANTLE_SHELTER, 1, target).orElseThrow();
        List<ScriptCall> calls = calls(plan);

        ScriptCall pillar = calls.stream()
                .filter(call -> "pillar_up".equals(call.script()))
                .findFirst()
                .orElseGet(() -> fail("Уборка висящей кроны обязана строить опору (pillar_up), иначе дух с земли "
                        + "не достанет ствол на y=85..97. План: " + calls));
        assertTrue(
                ((Integer) pillar.params().get("height")) >= 10,
                "Опора обязана довести духа до верхних брёвен y≈97, а не на пару блоков. План: " + pillar.params());
        assertTrue(
                calls.stream()
                        .anyMatch(call -> "acquire".equals(call.script())
                                && "minecraft:cobblestone".equals(call.params().get("item"))),
                "У духа в кроне пустой инвентарь: план обязан сначала взять булыжник (сундуки двора/добыча), "
                        + "иначе pillar_up падает «нет материала для столба» — так observe16 и остался без [clear]. "
                        + "План: " + calls);
        assertEquals(
                "minecraft:cobblestone",
                pillar.params().get("block"),
                "pillar_up обязан ставить именно добытый булыжник, а не выбирать пустую землю. План: "
                        + pillar.params());
        assertTrue(
                calls.stream().anyMatch(call -> "pillar_down".equals(call.script())),
                "Временную опору надо разобрать, иначе уборка оставит в поселении свой столб. План: " + calls);
        assertTrue(
                calls.stream()
                        .anyMatch(call -> "clear_area".equals(call.script())
                                && ((Integer) call.params().get("y2")) >= 96),
                "Рубка обязана доставать до верхних брёвен и листвы y≈97, а не только до нижнего блока. План: "
                        + calls);
    }

    @Test
    void liveWideTidySet_Plan_SplitsClearBoxesUnderScriptLimit() {
        StageSite site = TownFixtures.flatSite();
        List<PlannedBlock> blocks = List.of(
                new PlannedBlock(new BlockCoord(-300, 84, -600), StagePlans.AIR),
                new PlannedBlock(new BlockCoord(-100, 84, -400), StagePlans.AIR));
        TicketTarget target = TicketTarget.at(GROUND)
                .withWork(new StageWork(site, blocks))
                .withRelocate(new BlockCoord(-300, 84, -600), new BlockCoord(-100, 84, -600));
        TaskNode plan = StagePlans.plan(BoardCase.DISMANTLE_SHELTER, 1, target).orElseThrow();

        List<ScriptCall> clears = calls(plan).stream()
                .filter(call -> "clear_area".equals(call.script()))
                .toList();
        assertTrue(!clears.isEmpty(), "План уборки обязан содержать clear_area. План: " + calls(plan));
        for (ScriptCall call : clears) {
            int volume =
                    ((Integer) call.params().get("x2") - (Integer) call.params().get("x1") + 1)
                            * ((Integer) call.params().get("y2")
                                    - (Integer) call.params().get("y1")
                                    + 1)
                            * ((Integer) call.params().get("z2")
                                    - (Integer) call.params().get("z1")
                                    + 1);
            assertTrue(
                    volume <= CatalogLimits.BUILD_VOLUME_MAX,
                    "Живой тикет собрал пятна по всей базе: clear_area с объёмом " + volume + " больше предела "
                            + CatalogLimits.BUILD_VOLUME_MAX + " падает с IllegalArgumentException (observe16: "
                            + "«Объём clear_area 4352 больше предела 4096»). План: " + clears);
        }
    }

    private static List<PlannedBlock> crownBlocks(JsonNode fixture) {
        List<BlockCoord> cells = new ArrayList<>();
        for (JsonNode entry : fixture.get("blocks")) {
            int x = entry.get("x").asInt();
            int y = entry.get("y").asInt();
            int z = entry.get("z").asInt();
            String block = entry.get("block").asText();
            boolean inCrown = x >= -168 && x <= -158 && z >= -511 && z <= -503 && y >= 85;
            if (inCrown && (block.endsWith("_log") || block.endsWith("_leaves"))) {
                cells.add(new BlockCoord(x, y, z));
            }
        }
        cells.sort(Comparator.naturalOrder());
        return cells.stream()
                .map(cell -> new PlannedBlock(cell, StagePlans.AIR))
                .toList();
    }

    private static List<ScriptCall> calls(TaskNode node) {
        List<ScriptCall> found = new ArrayList<>();
        collect(node, found);
        return found;
    }

    private static void collect(TaskNode node, List<ScriptCall> found) {
        if (node instanceof ScriptCall call) {
            found.add(call);
            return;
        }
        if (node instanceof SequenceNode sequence) {
            sequence.steps().forEach(step -> collect(step, found));
            return;
        }
        if (node instanceof FallbackNode fallback) {
            fallback.steps().forEach(step -> collect(step, found));
        }
    }
}
