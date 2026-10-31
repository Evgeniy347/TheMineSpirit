package com.theminespirit.core.board;

import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.WoodSpecies;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.repair.RepairData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class RepairAudit {
    private static final int PERCENT = 100;
    private static final String AIR = "air";

    private RepairAudit() {}

    public enum Action {
        PLACE,
        CLEAR,
        WARN,
        REMOVE
    }

    public record Fix(BlockCoord cell, Action action, String wanted, String found, long removeAt) {
        public Fix {
            Objects.requireNonNull(cell, "Клетка правки не может быть null: иначе неясно, что чинить");
            Objects.requireNonNull(action, "Действие правки не может быть null: без него ремонт не начнётся");
            Objects.requireNonNull(wanted, "Ожидаемый блок не может быть null: по нему возвращают схему");
            Objects.requireNonNull(found, "Найденный блок не может быть null: по нему видно повреждение");
        }
    }

    public record Report(int total, int missing, boolean rebuild, List<Fix> fixes) {
        public Report {
            if (total < 0 || missing < 0 || missing > total) {
                throw new IllegalArgumentException("Аудит ремонта: нет " + missing + " из " + total
                        + " — недостача не может быть отрицательной или больше схемы");
            }
            fixes = List.copyOf(Objects.requireNonNull(fixes, "Правки ремонта не могут быть null: пустые — список"));
        }

        public boolean damaged() {
            return !fixes.isEmpty();
        }
    }

    public interface WorldView {
        String blockAt(BlockCoord cell);

        boolean playerPlaced(BlockCoord cell);
    }

    public static Report audit(
            Schematic schematic,
            BlockCoord origin,
            BuildRotation rotation,
            WorldView world,
            RepairData rules,
            Map<BlockCoord, Long> firstSeen,
            long now) {
        Objects.requireNonNull(schematic, "Схема ремонта не может быть null: чинить не по чему");
        Objects.requireNonNull(origin, "Начало постройки не может быть null: без него клетки схемы не найти");
        Objects.requireNonNull(rotation, "Поворот постройки не может быть null: иначе схема ляжет не туда");
        Objects.requireNonNull(world, "Мир ремонта не может быть null: сравнивать не с чем");
        Objects.requireNonNull(rules, "Правила ремонта не могут быть null: пороги берутся из данных");
        Objects.requireNonNull(firstSeen, "Первые отметки предупреждений не могут быть null: пустые — пустая карта");
        Map<BlockCoord, String> expected = expected(schematic, origin, rotation);
        Set<String> soft = soft(rules);
        List<Fix> fixes = new ArrayList<>();
        int missing = 0;
        for (Map.Entry<BlockCoord, String> entry : expected.entrySet()) {
            BlockCoord cell = entry.getKey();
            String wanted = entry.getValue();
            String found = id(world.blockAt(cell));
            if (same(wanted, found) || soft.contains(wanted)) {
                continue;
            }
            missing++;
            fixes.add(fix(cell, wanted, found, world, rules, firstSeen, now));
        }
        if (rebuild(missing, expected.size(), rules.rebuildPercent())) {
            return new Report(expected.size(), missing, true, List.of());
        }
        Set<BlockCoord> occupiedByScheme = new HashSet<>(expected.keySet());
        for (BlockCoord cell : schematic.airCells(origin, rotation)) {
            String found = id(world.blockAt(cell));
            if (occupiedByScheme.contains(cell) || AIR.equals(found)) {
                continue;
            }
            fixes.add(fix(cell, AIR, found, world, rules, firstSeen, now));
        }
        fixes.sort(Comparator.comparingInt((Fix fix) -> fix.cell().y())
                .thenComparingInt(fix -> fix.cell().x())
                .thenComparingInt(fix -> fix.cell().z()));
        return new Report(expected.size(), missing, false, fixes);
    }

    public static Map<String, Integer> need(Report report) {
        Objects.requireNonNull(report, "Отчёт ремонта не может быть null: нехватку берут из него");
        Map<String, Integer> need = new LinkedHashMap<>();
        for (Fix fix : report.fixes()) {
            if (fix.action() == Action.PLACE) {
                need.merge(id(fix.wanted()), 1, Integer::sum);
            }
        }
        return Map.copyOf(need);
    }

    public static boolean rebuild(int missing, int total, int rebuildPercent) {
        if (missing < 0 || total < 0 || missing > total) {
            throw new IllegalArgumentException("Аудит ремонта: нет " + missing + " из " + total
                    + " — недостача не может быть отрицательной или больше схемы");
        }
        if (rebuildPercent < 0 || rebuildPercent > PERCENT) {
            throw new IllegalArgumentException("Порог перестройки " + rebuildPercent + "% вне [0, " + PERCENT
                    + "]: по нему не понять, ремонтировать или строить заново");
        }
        return StageAudit.unbuilt(missing, total, rebuildPercent);
    }

    private static Map<BlockCoord, String> expected(Schematic schematic, BlockCoord origin, BuildRotation rotation) {
        Map<BlockCoord, String> expected = new LinkedHashMap<>();
        for (Schematic.PlacedEntry entry : schematic.entries(origin, rotation)) {
            if (!entry.optional()) {
                expected.put(entry.pos(), id(entry.block()));
            }
        }
        return expected;
    }

    private static Set<String> soft(RepairData rules) {
        Set<String> soft = new HashSet<>();
        rules.softBlocks().forEach(block -> soft.add(id(block)));
        return soft;
    }

    private static Fix fix(
            BlockCoord cell,
            String wanted,
            String found,
            WorldView world,
            RepairData rules,
            Map<BlockCoord, Long> firstSeen,
            long now) {
        if (world.playerPlaced(cell)) {
            long first = firstSeen.getOrDefault(cell, now);
            long removeAt = first + rules.warnTicks();
            Action action = now >= removeAt ? Action.REMOVE : Action.WARN;
            return new Fix(cell, action, name(wanted), name(found), removeAt);
        }
        Action action = AIR.equals(wanted) ? Action.CLEAR : Action.PLACE;
        return new Fix(cell, action, name(wanted), name(found), 0);
    }

    private static boolean same(String wanted, String found) {
        return wanted.equals(found) || WoodSpecies.sameRole(found, wanted);
    }

    private static String id(String block) {
        String value = block == null || block.isBlank() ? "minecraft:air" : block;
        return value.startsWith("minecraft:") ? value.substring("minecraft:".length()) : value;
    }

    private static String name(String key) {
        if (AIR.equals(key)) {
            return "minecraft:air";
        }
        return key.contains(":") ? key : "minecraft:" + key;
    }
}
