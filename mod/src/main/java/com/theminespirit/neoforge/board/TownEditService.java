package com.theminespirit.neoforge.board;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.theminespirit.core.board.SettlementLevel;
import com.theminespirit.core.board.StageRules;
import com.theminespirit.core.inventory.api.JsonResource;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.rest.ApiException;
import com.theminespirit.core.rest.ErrorCode;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.FenceLine;
import com.theminespirit.core.town.PathLine;
import com.theminespirit.core.town.Placement;
import com.theminespirit.core.town.SettlementPlot;
import com.theminespirit.core.town.TownEntry;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.TownRules;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;

public final class TownEditService {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ServerLevel level;

    private TownEditService(ServerLevel level) {
        this.level = Objects.requireNonNull(level, "level не может быть null: карта берётся из мира");
    }

    public static TownEditService of(ServerLevel level) {
        return new TownEditService(level);
    }

    public String view() {
        TownRules rules = DataTables.INSTANCE.town().current();
        TownPlan plan = plan(rules);
        ObjectNode root = JSON.createObjectNode();
        SettlementPlot home = SettlementPlotStore.get(level)
                .plot()
                .orElseThrow(() -> new ApiException(
                        ErrorCode.INVALID_PLAN, "высота поселения ещё не обмеряна: дождитесь пересборки", Map.of()));
        ObjectNode anchor = root.putObject("anchor");
        anchor.put("x", home.anchor().x())
                .put("y", SettlementLevel.of(home))
                .put("z", home.anchor().z());
        ObjectNode region = root.putObject("region");
        region.put("minX", plan.region().minX())
                .put("minZ", plan.region().minZ())
                .put("width", plan.region().width())
                .put("depth", plan.region().depth())
                .put("area", plan.area());
        region.put("cellCount", plan.region().count());
        TownEditCheck.runs(region.putArray("cells"), plan.region());
        root.put("stage", SettlementStageService.of(level).view().stage());
        ArrayNode plots = root.putArray("plots");
        for (Placement place : plan.placements()) {
            ObjectNode plot = plots.addObject();
            plot.put("id", place.id()).put("name", place.name()).put("stage", place.stage());
            plot.put("x", place.x()).put("z", place.z());
            plot.put("width", place.width()).put("depth", place.depth());
            plot.put("facing", place.facing().id());
        }
        ArrayNode streets = root.putArray("streets");
        street(streets, "main", plan.streets().main());
        street(streets, "side", plan.streets().side());
        street(streets, "ring", plan.streets().ring());
        ObjectNode fence = root.putObject("fence");
        ArrayNode posts = fence.putArray("posts");
        for (ColumnMask.Cell post : plan.fence().posts()) {
            posts.addArray().add(post.x()).add(post.z());
        }
        ArrayNode gates = fence.putArray("gates");
        for (int gate : plan.fence().gates()) {
            ColumnMask.Cell cell = plan.fence().posts().get(gate);
            gates.addArray().add(cell.x()).add(cell.z());
        }
        ArrayNode paths = root.putArray("paths");
        for (PathLine path : plan.paths()) {
            ObjectNode line = paths.addObject();
            line.put("id", path.id());
            ArrayNode cells = line.putArray("cells");
            for (ColumnMask.Cell cell : path.cells()) {
                cells.addArray().add(cell.x()).add(cell.z());
            }
        }
        ArrayNode unplaced = root.putArray("unplaced");
        plan.unplaced().forEach(unplaced::add);
        return root.toString();
    }

    public String edit(String body, boolean dryRun) {
        TownRules rules = DataTables.INSTANCE.town().current();
        TownPlan plan = plan(rules);
        JsonNode root = JsonResource.parse(body, "town");
        JsonNode ops = root.get("ops");
        if (ops == null || !ops.isArray()) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "нет массива ops: правка без операций ничего не меняет",
                    Map.of("field", "ops"));
        }
        List<Placement> placements = new ArrayList<>(plan.placements());
        List<PathLine> paths = new ArrayList<>(plan.paths());
        List<ColumnMask.Cell> posts = plan.fence().posts();
        List<Integer> gates = new ArrayList<>(plan.fence().gates());
        Set<String> placedIds = new HashSet<>();
        placements.forEach(place -> placedIds.add(place.id()));
        List<String> errors = new ArrayList<>();
        for (JsonNode op : ops) {
            String kind = text(op, "op");
            switch (kind) {
                case "move_plot" -> {
                    String id = text(op, "id");
                    Placement old = placements.stream()
                            .filter(p -> p.id().equals(id))
                            .findFirst()
                            .orElse(null);
                    if (old == null) {
                        errors.add("участка '" + id + "' нет в плане");
                    } else {
                        placements.set(
                                placements.indexOf(old),
                                new Placement(
                                        id,
                                        old.name(),
                                        old.stage(),
                                        number(op, "x"),
                                        number(op, "z"),
                                        old.width(),
                                        old.depth(),
                                        old.facing()));
                    }
                }
                case "add_plot" -> {
                    String id = text(op, "id");
                    if (placedIds.contains(id)) {
                        errors.add("участок '" + id + "' уже есть в плане");
                        break;
                    }
                    TownEntry entry = rules.entry(id).orElse(null);
                    if (entry == null) {
                        errors.add("участка '" + id + "' нет в каталоге town.json");
                        break;
                    }
                    placements.add(new Placement(
                            id,
                            entry.name(),
                            stageOf(id),
                            number(op, "x"),
                            number(op, "z"),
                            entry.width(),
                            entry.depth(),
                            Placement.Facing.SOUTH));
                    placedIds.add(id);
                }
                case "remove_plot" -> {
                    String id = text(op, "id");
                    if (!placements.removeIf(place -> place.id().equals(id))) {
                        errors.add("участка '" + id + "' нет в плане");
                    }
                    placedIds.remove(id);
                }
                case "set_gates" -> {
                    gates.clear();
                    for (JsonNode gate : array(op, "gates")) {
                        int x = gate.get(0).asInt();
                        int z = gate.get(1).asInt();
                        int index = TownEditCheck.indexOf(posts, x, z);
                        if (index < 0) {
                            errors.add("ворота " + x + "," + z + " не стоят на линии ограды");
                        } else {
                            gates.add(index);
                        }
                    }
                }
                case "add_path" -> {
                    String id = text(op, "id");
                    List<ColumnMask.Cell> cells = new ArrayList<>();
                    for (JsonNode point : array(op, "points")) {
                        cells.add(new ColumnMask.Cell(
                                point.get(0).asInt(), point.get(1).asInt()));
                    }
                    paths.removeIf(path -> path.id().equals(id));
                    paths.add(new PathLine(id, cells));
                }
                case "remove_path" -> {
                    String id = text(op, "id");
                    if (!paths.removeIf(path -> path.id().equals(id))) {
                        errors.add("тропинки '" + id + "' нет в плане");
                    }
                }
                default -> errors.add("неизвестная операция '" + kind + "'");
            }
        }
        TownEditCheck.validate(plan, rules, placements, paths, posts, gates, errors);
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_PLAN, String.join("; ", errors), Map.of("errors", errors));
        }
        ObjectNode answer = JSON.createObjectNode();
        answer.put("ok", true);
        answer.put("dry_run", dryRun);
        answer.put("plots", placements.size());
        answer.put("paths", paths.size());
        if (!dryRun) {
            TownPlan next = new TownPlan(
                    plan.anchor(),
                    plan.region(),
                    plan.streets(),
                    placements,
                    new FenceLine(posts, gates),
                    plan.unplaced(),
                    paths);
            SettlementPlotStore.get(level).refitTown(next);
        }
        return answer.toString();
    }

    private TownPlan plan(TownRules rules) {
        return SettlementPlotStore.get(level)
                .town()
                .filter(plan -> plan.region().width() == 2 * rules.ground().probeRadius() + 1)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.INVALID_PLAN,
                        "плана поселения ещё нет или он размечен прежним обмером: дождитесь пересборки",
                        Map.of()));
    }

    private static void street(ArrayNode streets, String kind, ColumnMask mask) {
        if (mask.isEmpty()) {
            return;
        }
        ObjectNode street = streets.addObject();
        street.put("kind", kind)
                .put("minX", mask.minX())
                .put("minZ", mask.minZ())
                .put("width", mask.width())
                .put("depth", mask.depth());
        street.put("cellCount", mask.count());
        TownEditCheck.runs(street.putArray("cells"), mask);
    }

    private static int stageOf(String id) {
        for (StageRules.Stage stage : StageRules.current().stages()) {
            if (stage.plan().contains(id)) {
                return stage.stage();
            }
        }
        return 1;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST, "у операции нет поля '" + field + "'", Map.of("field", field));
        }
        return value.asText();
    }

    private static int number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToInt()) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST, "у операции нет числа '" + field + "'", Map.of("field", field));
        }
        return value.asInt();
    }

    private static JsonNode array(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray()) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST, "у операции нет массива '" + field + "'", Map.of("field", field));
        }
        return value;
    }
}
