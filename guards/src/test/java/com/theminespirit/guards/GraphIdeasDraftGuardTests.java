package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphEvent;
import com.theminespirit.core.board.graph.SupplyGraph;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GraphIdeasDraftGuardTests {
    private static final Path DRAFT = Path.of("docs", "research", "graph-ideas-nodes.json");

    @Test
    void draft_ValidJson_ParsesAsSelfContainedGraph() throws IOException {
        SupplyGraph graph = SupplyGraph.parse(Files.readString(DRAFT, StandardCharsets.UTF_8), DRAFT.toString());

        assertTrue(graph.items().size() >= 50, "В черновике меньше 50 предметов: кандидаты узлов потерялись");
        assertTrue(graph.buildings().size() >= 20, "В черновике меньше 20 построек: кандидаты потерялись");
        assertTrue(graph.ways().size() >= 30, "В черновике меньше 30 путей: кандидаты потерялись");
        assertTrue(graph.events().size() >= 20, "В черновике меньше 20 событий: кандидаты потерялись");
        assertTrue(
                graph.ways().stream().anyMatch(way -> !graph.implemented(way.id())),
                "В черновике нет ни одной заглушки implemented=false: новые узлы не отличить от готовых");
    }

    @Test
    void draft_EventReferences_AllKnownInGraph() throws IOException {
        SupplyGraph graph = SupplyGraph.parse(Files.readString(DRAFT, StandardCharsets.UTF_8), DRAFT.toString());
        List<String> broken = new ArrayList<>();

        for (GraphEvent event : graph.events().values()) {
            for (GraphEvent.Task task : event.tasks()) {
                if (task.building() != null && !graph.buildings().containsKey(task.building())) {
                    broken.add(event.name() + " -> " + task.id() + " строит неизвестное " + task.building());
                }
                for (String item : task.consumes().keySet()) {
                    if (!known(graph, item)) {
                        broken.add(event.name() + " -> " + task.id() + " расходует неизвестное " + item);
                    }
                }
            }
            for (String node : event.push().keySet()) {
                if (!known(graph, node)) {
                    broken.add(event.name() + " -> push неизвестного " + node);
                }
            }
        }

        assertEquals(
                List.of(),
                broken,
                "Событие черновика ссылается на узел, которого нет в графе: задание не соберётся, "
                        + "а связь потеряется молча. Как надо: объявить предмет, группу или постройку в этом же файле");
    }

    @Test
    void draft_BrokenWayReference_IsReported() throws IOException {
        String json = Files.readString(DRAFT, StandardCharsets.UTF_8);
        String broken = json.replaceFirst("\"makes\": \"rest\"", "\"makes\": \"rest_missing\"");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> SupplyGraph.parse(broken, "broken-draft"),
                "Черновик с неизвестным предметом обязан падать при разборе, а не проходить молча");

        assertTrue(
                error.getMessage().contains("rest_missing"),
                "Сторож не назвал неизвестный предмет rest_missing: черновик с битой ссылкой прошёл бы молча");
    }

    private static boolean known(SupplyGraph graph, String node) {
        if (node.startsWith("building:")) {
            return graph.buildings().containsKey(node.substring("building:".length()));
        }
        return graph.items().containsKey(node) || graph.isGroup(node) || graph.isTool(node);
    }
}
