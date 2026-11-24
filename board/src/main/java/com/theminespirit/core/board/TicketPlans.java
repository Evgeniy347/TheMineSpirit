package com.theminespirit.core.board;

import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.api.BuildSchematicParams;
import com.theminespirit.core.script.api.CatalogLimits;
import com.theminespirit.core.town.StageSites;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class TicketPlans {
    public static final Set<BoardCase> EXTERNAL_CASES = Set.of(BoardCase.EAT_TO_FULL);
    public static final String MINE_DESCENT_SCHEME = StageSites.MINE_DESCENT_SCHEME;
    public static final String MINE_DESCENT_ROTATION = StageSites.MINE_DESCENT_ROTATION;
    static final String BLOCK = "block";
    static final String ITEM = "item";
    static final String COUNT = "count";
    static final String CONTAINER = "container";
    static final String NEAREST = "nearest";
    static final String SPECIES = "species";
    static final String RADIUS = "radius";
    static final String TORCH = "minecraft:torch";
    static final String BUILD_SCHEMATIC = "build_schematic";
    static final String FILE = "file";
    static final String FILL = "fill";
    static final String ACQUIRE = "acquire";
    static final String CRAFT = "craft";
    static final String PLACE = "place";
    static final String WAIT = "wait";
    static final String TICKS = "ticks";
    static final String CLEAR_AREA = "clear_area";
    static final String WITHDRAW = "withdraw";
    static final String DEFAULT_SCHEME = "stone_house";
    static final String WALL_SCHEME = "wall_3";
    static final String DEFAULT_LURE_ITEM = "wheat";
    static final String ENCHANT_ROOM_SCHEME = "enchant_room_v1";
    static final String ENCHANT_ROOM_SHELVES_SCHEME = "enchant_room_v2";
    static final String ENCHANT_ROOM_HALL_SCHEME = "enchant_room_v3";

    private TicketPlans() {}

    public static int fenceParts() {
        return BoardConfig.current().plans().fenceParts();
    }

    public static int mineTunnelSize() {
        return BoardConfig.current().plans().mineTunnelSize();
    }

    static PlanTuning plans() {
        return BoardConfig.current().plans();
    }

    public static Optional<String> craftItem(BoardCase boardCase) {
        Objects.requireNonNull(boardCase, "Кейс крафта не может быть null: без него неизвестен предмет");
        if (ToolSet.isTool(boardCase)) {
            return Optional.of(ToolSet.itemId(boardCase));
        }
        return switch (boardCase) {
            case CRAFTING_STATION -> Optional.of("minecraft:crafting_table");
            case CRAFT_ANVIL -> Optional.of("minecraft:anvil");
            case CRAFT_SMITHING_TABLE -> Optional.of("minecraft:smithing_table");
            case CRAFT_SMOKER -> Optional.of("minecraft:smoker");
            case CRAFT_FURNACE -> Optional.of("minecraft:furnace");
            case CRAFT_TORCHES -> Optional.of(TORCH);
            case STOCK_CHESTS -> Optional.of("minecraft:chest");
            case ENCHANT_SHELVES -> Optional.of("minecraft:bookshelf");
            case PLACE_BEEHIVE -> Optional.of(HiveRules.CRAFTED_BEEHIVE);
            default -> Optional.empty();
        };
    }

    public static TaskNode plan(BoardCase boardCase, TicketTarget target) {
        return plan(boardCase, 1, target);
    }

    public static TaskNode plan(BoardCase boardCase, int level, TicketTarget target) {
        Objects.requireNonNull(boardCase, "Кейс плана не может быть null: без него непонятно, что строить");
        if (level < 1) {
            throw new IllegalArgumentException("Уровень плана " + level + " меньше 1: первая постройка "
                    + "ставится уровнем 1, нулевого уровня не существует");
        }
        int max = BuildingLevels.maxLevel(boardCase);
        if (level > max) {
            throw new IllegalArgumentException("Уровень " + level + " кейса " + boardCase + " выше максимального " + max
                    + ": уровень постройки не существует и выдавать его нельзя");
        }
        var staged = StagePlans.plan(boardCase, level, target).or(() -> RescuePlans.plan(boardCase, target));
        if (staged.isPresent()) {
            return staged.orElseThrow();
        }
        return switch (boardCase) {
            case BUILD_MINE -> TicketStructurePlans.buildMineLevel(level, target);
            case BUILD_PEN -> TicketStructurePlans.buildPenLevel(level, target);
            case ENCHANT_ROOM -> TicketStructurePlans.enchantRoomLevel(level, target);
            case BUILD_HOUSE -> TicketWorkPlans.buildHouse(level, target);
            case BUILD_ORCHARD -> TicketWorkPlans.buildOrchard(target);
            case BUILD_WAREHOUSE -> TicketStructurePlans.buildWarehouseLevel(level, target);
            default -> defaultPlan(boardCase, target, level);
        };
    }

    private static TaskNode defaultPlan(BoardCase boardCase, TicketTarget target, int level) {
        return DefaultPlans.plan(boardCase, target, level);
    }

    public static BlockCoord mineDescentOrigin(TicketTarget target) {
        Objects.requireNonNull(target, "Цель задания не может быть null: спуск ставится по плану или в цели");
        return target.work() == null ? target.target() : target.work().site().mineEntrance();
    }

    static TaskNode takeFrom(String item, int count) {
        return new FallbackNode(List.of(
                new ScriptCall(
                        WITHDRAW,
                        Map.of(
                                CONTAINER,
                                NEAREST,
                                ITEM,
                                item,
                                COUNT,
                                Math.max(1, Math.min(count, CatalogLimits.COUNT_MAX)),
                                RADIUS,
                                plans().supplyContainerRadius())),
                new ScriptCall(WAIT, Map.of(TICKS, CatalogLimits.WAIT_TICKS_MIN))));
    }

    static TaskNode takeFloor(String item) {
        return new FallbackNode(List.of(
                new ScriptCall(
                        WITHDRAW,
                        Map.of(
                                CONTAINER,
                                NEAREST,
                                ITEM,
                                item,
                                COUNT,
                                plans().mineFloorTake(),
                                RADIUS,
                                plans().supplyContainerRadius())),
                new ScriptCall(WAIT, Map.of(TICKS, CatalogLimits.WAIT_TICKS_MIN))));
    }

    static TaskNode craft(String item, int count, TicketTarget target) {
        return sequence(
                gotoTarget(target),
                new ScriptCall(CRAFT, Map.of(ITEM, item, COUNT, count, RADIUS, CatalogLimits.STATION_RADIUS_DEFAULT)));
    }

    static String bedItem(TicketTarget target) {
        if (target.craftItem() != null) {
            return target.craftItem();
        }
        return WoolRules.CRAFTED_BED;
    }

    static TaskNode transferItem(TicketTarget target) {
        if (target.transferTo() == null) {
            throw new IllegalArgumentException("Передача без получателя: deliver не знает, кому нести груз, "
                    + "а withdraw — чей инвентарь пополнять");
        }
        if (target.supplyItem() == null || target.supplyCount() <= 0) {
            throw new IllegalArgumentException(
                    "Передача без предмета или количества: " + "нечего нести и нечем закрыть нехватку");
        }
        int count = Math.min(target.supplyCount(), CatalogLimits.COUNT_MAX);
        if (target.transferFrom() != null) {
            return new ScriptCall(
                    "deliver", Map.of(ITEM, target.supplyItem(), COUNT, count, "to", "spirit:" + target.transferTo()));
        }
        return sequence(
                gotoTarget(target),
                new FallbackNode(List.of(
                        new ScriptCall(
                                WITHDRAW,
                                Map.of(
                                        CONTAINER,
                                        "auto",
                                        ITEM,
                                        target.supplyItem(),
                                        COUNT,
                                        count,
                                        RADIUS,
                                        CatalogLimits.CONTAINER_RADIUS_DEFAULT)),
                        new ScriptCall(WAIT, Map.of(TICKS, CatalogLimits.WAIT_TICKS_MIN)))));
    }

    static ScriptCall schematic(String scheme, TicketTarget target) {
        return schematic(scheme, target.target(), target, null);
    }

    static ScriptCall schematic(String scheme, TicketTarget target, @Nullable String previous) {
        return schematic(scheme, target.target(), target, previous);
    }

    static ScriptCall schematic(String scheme, BlockCoord origin, TicketTarget target) {
        return schematic(scheme, origin, target, null);
    }

    static ScriptCall schematic(String scheme, BlockCoord origin, TicketTarget target, @Nullable String previous) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put(FILE, scheme);
        params.put("x", origin.x());
        params.put("y", origin.y());
        params.put("z", origin.z());
        if (target.wood() != null) {
            params.put(BuildSchematicParams.WOOD, target.wood());
        }
        if (previous != null) {
            params.put(BuildSchematicParams.PREVIOUS, previous);
        }
        return new ScriptCall(BUILD_SCHEMATIC, Map.copyOf(params));
    }

    static ScriptCall gotoTarget(TicketTarget target) {
        return gotoPoint(target.target());
    }

    static ScriptCall gotoPoint(BlockCoord point) {
        return new ScriptCall("goto", Map.of("x", point.x(), "y", point.y(), "z", point.z()));
    }

    static ScriptCall demolishBox(BlockCoord center, int radius) {
        return new ScriptCall(
                CLEAR_AREA,
                Map.of(
                        "x1",
                        center.x() - radius,
                        "y1",
                        center.y(),
                        "z1",
                        center.z() - radius,
                        "x2",
                        center.x() + radius,
                        "y2",
                        center.y() + 2,
                        "z2",
                        center.z() + radius,
                        "demolish",
                        true));
    }

    static TaskNode sequence(TaskNode... steps) {
        List<TaskNode> list = new ArrayList<>(steps.length);
        for (TaskNode step : steps) {
            list.add(step);
        }
        return new SequenceNode(list);
    }
}
