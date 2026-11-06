package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.Ingredient;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StageWork;
import com.theminespirit.core.town.TownFixtures;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OrchardPathLiveTests {
    private static final Path DIR = Path.of("src/test/resources/live/orchardpath");
    private static final Path TICKET = DIR.resolve("task-graph_make_dirt_path.json");
    private static final Path BOARD = DIR.resolve("board.json");
    private static final BlockCoord ANCHOR = new BlockCoord(-171, 69, -489);
    private static final String DIRT_PATH = "dirt_path";

    @Test
    void liveDirtPathTicket_Plan_TramplesGroundInsteadOfGatheringUnobtainableBlock() throws IOException {
        JsonNode ticket = read(TICKET);
        JsonNode board = read(BOARD);
        assertTrue(find(board, ticket.get("id").asText()) != null, "На живой доске нет тикета " + ticket.get("id"));
        assertTrue(
                DIRT_PATH.equals(bare(ticket.get("supplyItem").asText())),
                "Живой тикет обязан просить дорожку сада: " + ticket);

        TaskNode plan = TicketPlans.plan(
                BoardCase.valueOf(ticket.get("case").asText()),
                ticket.get("level").asInt(),
                TicketTarget.at(ANCHOR)
                        .withSupply(DIRT_PATH, ticket.get("supplyCount").asInt()));
        List<ScriptCall> calls = calls(plan);

        assertTrue(
                calls.stream()
                        .anyMatch(call -> "gather".equals(call.script())
                                && DIRT_PATH.equals(bare(call.params().get("block")))),
                "У дорожки нет задачи-производителя: план только «добыть блок dirt_path», которого в мире нет. "
                        + "Дорожку топчут лопатой по земле/траве. План: " + plan);
        assertFalse(
                calls.stream()
                        .anyMatch(call -> "acquire".equals(call.script())
                                && DIRT_PATH.equals(bare(call.params().get("item")))),
                "Дорожка не добывается как предмет из мира: её делают лопатой. План: " + plan);
        assertTrue(
                calls.stream()
                        .anyMatch(call -> "withdraw".equals(call.script())
                                && String.valueOf(call.params().get("item")).endsWith("_shovel")),
                "Перед топтанием нужна лопата из сундука: " + plan);
    }

    @Test
    void orchardPlan_DirtPath_IsActionPlacedAndNotAStockMaterial() {
        StageSite site = TownFixtures.flatSite();
        TicketTarget target = TicketTarget.at(site.workYard()).withWork(new StageWork(site, List.of()));
        TaskNode plan = TicketPlans.plan(BoardCase.BUILD_ORCHARD, 1, target);
        List<ScriptCall> calls = calls(plan);

        assertFalse(
                calls.stream()
                        .anyMatch(call -> "acquire".equals(call.script())
                                && DIRT_PATH.equals(bare(call.params().get("item")))),
                "Сад не покупает dirt_path: тропинка топчется по схеме. План: " + plan);
        assertTrue(
                calls.stream().anyMatch(call -> "build_schematic".equals(call.script())),
                "Сад ставится по схеме, где dirt_path — действие лопатой: " + plan);
        Map<String, Integer> need = CaseMaterials.need(plan, materialSource());
        assertFalse(
                need.containsKey("minecraft:" + DIRT_PATH) || need.containsKey(DIRT_PATH),
                "Гейт материалов сада не должен ждать dirt_path: его производят действием, а не складом. Нужда: "
                        + need);
    }

    private static MaterialSource materialSource() {
        return new MaterialSource() {
            @Override
            public int outputCount(String item) {
                return 0;
            }

            @Override
            public List<Ingredient> ingredientsFor(String item) {
                return List.of();
            }

            @Override
            public Map<String, Integer> schematicBlocks(String file) {
                Schematic schematic = DataTables.INSTANCE
                        .presets()
                        .current()
                        .byId(file)
                        .orElseThrow()
                        .schematic();
                return PresetLibrary.countMaterials(schematic);
            }
        };
    }

    private static JsonNode find(JsonNode board, String id) {
        for (JsonNode task : board.get("tasks")) {
            if (id.equals(task.get("id").asText())) {
                return task;
            }
        }
        return null;
    }

    private static String bare(Object id) {
        String text = String.valueOf(id);
        if (text.startsWith("minecraft:")) {
            text = text.substring("minecraft:".length());
        }
        int state = text.indexOf('[');
        return state < 0 ? text : text.substring(0, state);
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
            return;
        }
        throw new IllegalArgumentException("Узел " + node.getClass().getSimpleName() + " не разбирается тестом");
    }

    private static JsonNode read(Path path) throws IOException {
        return new ObjectMapper().readTree(Files.readString(path, StandardCharsets.UTF_8));
    }
}
