package com.theminespirit.core.board;

import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.PlotLeveling;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public final class TicketTargetCodec {
    private static final String TARGET = "Target";
    private static final String SCHEME = "Scheme";
    private static final String FENCE = "Fence";
    private static final String LURE = "Lure";
    private static final String SUPPLY = "Supply";
    private static final String SUPPLY_COUNT = "SupplyCount";
    private static final String CRATER = "Crater";
    private static final String TOOL_COUNT = "ToolCount";
    private static final String SPECIES = "Species";
    private static final String FROM = "From";
    private static final String TO = "To";
    private static final String DROPS = "Drops";
    private static final String TRANSFER_FROM = "TransferFrom";
    private static final String TRANSFER_TO = "TransferTo";
    private static final String CRAFT = "Craft";
    private static final String COUNT = "Count";
    private static final String WOOD = "Wood";
    private static final String BUILDING = "Building";
    private static final String LEVELING_BLOCK = "LevelingBlock";
    private static final String LEVELING_CUT = "LevelingCut";
    private static final String LEVELING_FILL = "LevelingFill";
    private static final String LEVELING_VOIDS = "LevelingVoids";
    private static final String DEPOSIT = "Deposit";

    private TicketTargetCodec() {}

    public static Optional<Map<String, Object>> encode(TicketTarget target) {
        Objects.requireNonNull(target, "Цель задания не может быть null: сохранять нечего");
        if (target.knowledge() != null || target.work() != null || target.rescue() != null || target.plan() != null) {
            return Optional.empty();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(TARGET, coords(target.target()));
        put(map, SCHEME, target.schemeFile());
        put(map, FENCE, target.fenceBlock());
        put(map, LURE, target.lureItem());
        put(map, SUPPLY, target.supplyItem());
        map.put(SUPPLY_COUNT, target.supplyCount());
        map.put(CRATER, target.craterBlocks());
        map.put(TOOL_COUNT, target.toolCount());
        put(map, SPECIES, target.species() == null ? null : target.species().name());
        putCoord(map, FROM, target.from());
        putCoord(map, TO, target.to());
        if (!target.dropItems().isEmpty()) {
            map.put(DROPS, target.dropItems());
        }
        put(
                map,
                TRANSFER_FROM,
                target.transferFrom() == null ? null : target.transferFrom().toString());
        put(
                map,
                TRANSFER_TO,
                target.transferTo() == null ? null : target.transferTo().toString());
        put(map, CRAFT, target.craftItem());
        map.put(COUNT, target.count());
        put(map, WOOD, target.wood());
        put(map, BUILDING, target.building());
        putLeveling(map, target.leveling());
        putCoord(map, DEPOSIT, target.deposit());
        return Optional.of(Map.copyOf(map));
    }

    // lib: NullAway не видит тип-аннотацию SelfEscape.@Nullable Plan у вложенного типа
    @SuppressWarnings("NullAway")
    public static TicketTarget decode(Map<String, Object> map) {
        Objects.requireNonNull(map, "Запись цели не может быть null: без неё задание не восстановить");
        PlotLeveling leveling = leveling(map);
        return new TicketTarget(
                requireCoord(map, TARGET),
                string(map, SCHEME),
                string(map, FENCE),
                string(map, LURE),
                string(map, SUPPLY),
                integer(map, SUPPLY_COUNT),
                integer(map, CRATER),
                integer(map, TOOL_COUNT),
                species(map),
                coord(map, FROM),
                coord(map, TO),
                null,
                strings(map, DROPS),
                uuid(map, TRANSFER_FROM),
                uuid(map, TRANSFER_TO),
                string(map, CRAFT),
                integer(map, COUNT),
                string(map, WOOD),
                string(map, BUILDING),
                leveling,
                null,
                null,
                coord(map, DEPOSIT),
                null);
    }

    private static void putLeveling(Map<String, Object> map, @Nullable PlotLeveling leveling) {
        if (leveling == null) {
            return;
        }
        map.put(LEVELING_BLOCK, leveling.block());
        map.put(LEVELING_CUT, flat(leveling.cut()));
        map.put(LEVELING_FILL, flat(leveling.fill()));
        map.put(LEVELING_VOIDS, flat(leveling.voids()));
    }

    private static @Nullable PlotLeveling leveling(Map<String, Object> map) {
        String block = string(map, LEVELING_BLOCK);
        if (block == null) {
            return null;
        }
        return new PlotLeveling(boxes(map, LEVELING_CUT), boxes(map, LEVELING_FILL), block, boxes(map, LEVELING_VOIDS));
    }

    private static List<Integer> flat(List<PlotLeveling.Box> boxes) {
        List<Integer> flat = new ArrayList<>(boxes.size() * 6);
        for (PlotLeveling.Box box : boxes) {
            flat.add(box.x1());
            flat.add(box.y1());
            flat.add(box.z1());
            flat.add(box.x2());
            flat.add(box.y2());
            flat.add(box.z2());
        }
        return List.copyOf(flat);
    }

    private static List<PlotLeveling.Box> boxes(Map<String, Object> map, String key) {
        List<Integer> flat = integers(map, key);
        if (flat.size() % 6 != 0) {
            throw new IllegalArgumentException("Запись " + key + " длины " + flat.size()
                    + ": коробка выравнивания это 6 чисел, проверьте NBT доски");
        }
        List<PlotLeveling.Box> boxes = new ArrayList<>(flat.size() / 6);
        for (int index = 0; index < flat.size(); index += 6) {
            boxes.add(new PlotLeveling.Box(
                    flat.get(index),
                    flat.get(index + 1),
                    flat.get(index + 2),
                    flat.get(index + 3),
                    flat.get(index + 4),
                    flat.get(index + 5)));
        }
        return List.copyOf(boxes);
    }

    private static void putCoord(Map<String, Object> map, String key, @Nullable BlockCoord coord) {
        if (coord != null) {
            map.put(key, coords(coord));
        }
    }

    private static List<Integer> coords(BlockCoord coord) {
        return List.of(coord.x(), coord.y(), coord.z());
    }

    private static BlockCoord requireCoord(Map<String, Object> map, String key) {
        BlockCoord coord = coord(map, key);
        if (coord == null) {
            throw new IllegalArgumentException(
                    "В записи цели нет " + key + ": задание без точки ставить нельзя, проверьте NBT доски");
        }
        return coord;
    }

    private static @Nullable BlockCoord coord(Map<String, Object> map, String key) {
        List<Integer> values = integers(map, key);
        if (values.isEmpty()) {
            return null;
        }
        if (values.size() != 3) {
            throw new IllegalArgumentException(
                    "Запись " + key + " длины " + values.size() + ": точка это 3 числа, проверьте NBT доски");
        }
        return new BlockCoord(values.get(0), values.get(1), values.get(2));
    }

    private static @Nullable Species species(Map<String, Object> map) {
        String name = string(map, SPECIES);
        if (name == null) {
            return null;
        }
        try {
            return Species.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException(
                    "Вид животного '" + name + "' неизвестен: цель не восстановить, проверьте сборку");
        }
    }

    private static @Nullable UUID uuid(Map<String, Object> map, String key) {
        String value = string(map, key);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException broken) {
            throw new IllegalArgumentException(
                    "Запись " + key + " не UUID: '" + value + "', проверьте NBT доски", broken);
        }
    }

    private static void put(Map<String, Object> map, String key, @Nullable String value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private static @Nullable String string(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException(
                    "Запись " + key + " не строка: " + value.getClass().getSimpleName() + ", проверьте NBT доски");
        }
        return text;
    }

    private static int integer(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return 0;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(
                    "Запись " + key + " не число: " + value.getClass().getSimpleName() + ", проверьте NBT доски");
        }
        return number.intValue();
    }

    private static List<String> strings(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return List.of();
        }
        List<String> strings = new ArrayList<>();
        for (Object element : list(value, key)) {
            if (!(element instanceof String text)) {
                throw new IllegalArgumentException(
                        "Запись " + key + " содержит не строку: " + element + ", проверьте NBT доски");
            }
            strings.add(text);
        }
        return List.copyOf(strings);
    }

    private static List<Integer> integers(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return List.of();
        }
        List<Integer> integers = new ArrayList<>();
        for (Object element : list(value, key)) {
            if (!(element instanceof Number number)) {
                throw new IllegalArgumentException(
                        "Запись " + key + " содержит не число: " + element + ", проверьте NBT доски");
            }
            integers.add(number.intValue());
        }
        return List.copyOf(integers);
    }

    private static List<?> list(Object value, String key) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException(
                    "Запись " + key + " не список: " + value.getClass().getSimpleName() + ", проверьте NBT доски");
        }
        return list;
    }
}
