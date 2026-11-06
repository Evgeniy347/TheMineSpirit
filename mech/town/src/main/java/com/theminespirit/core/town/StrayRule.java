package com.theminespirit.core.town;

import com.theminespirit.core.forestry.ChopTuning;
import com.theminespirit.core.forestry.TreeView;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.policy.StrayRules;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class StrayRule {
    private static final IdFilter STATIONS = IdFilter.of(List.of("minecraft:crafting_table", "minecraft:furnace"));

    public enum Fact {
        PLANNED,
        PLAYER,
        SPIRIT,
        PLANT,
        GROUND,
        LOG,
        IN_TREE,
        UNSUPPORTED
    }

    private StrayRule() {}

    public static boolean stray(String blockId, Set<Fact> facts, StrayRules protection) {
        Objects.requireNonNull(blockId, "Блок не может быть null: снос решается по его имени");
        Objects.requireNonNull(facts, "Признаки клетки не могут быть null: пустые задают пустым набором");
        if (facts.contains(Fact.PLANNED) || facts.contains(Fact.PLAYER)) {
            return false;
        }
        if (STATIONS.matches(blockId)) {
            return false;
        }
        if (facts.contains(Fact.LOG)) {
            return !facts.contains(Fact.IN_TREE);
        }
        if (facts.contains(Fact.GROUND) || protection.natural(blockId)) {
            return facts.contains(Fact.SPIRIT) && facts.contains(Fact.UNSUPPORTED);
        }
        return !facts.contains(Fact.PLANT);
    }

    public static Set<BlockCoord> treeLogs(BlockCoord log, TreeView view) {
        Objects.requireNonNull(log, "Бревно не может быть null: от него ищется дерево");
        Objects.requireNonNull(view, "Вид мира не может быть null: брёвна и листва читаются из мира");
        Set<BlockCoord> logs = new HashSet<>();
        Set<BlockCoord> seen = new HashSet<>();
        int leaves = 0;
        Deque<BlockCoord> queue = new ArrayDeque<>();
        queue.add(log);
        seen.add(log);
        logs.add(log);
        while (!queue.isEmpty() && logs.size() <= ChopTuning.MAX_LOGS) {
            BlockCoord current = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockCoord next = new BlockCoord(current.x() + dx, current.y() + dy, current.z() + dz);
                        if (!seen.add(next)) {
                            continue;
                        }
                        if (view.isLog(next)) {
                            logs.add(next);
                            queue.add(next);
                        } else if (view.isNaturalLeaves(next)) {
                            leaves++;
                        }
                    }
                }
            }
        }
        return leaves >= ChopTuning.MIN_NATURAL_LEAVES ? Set.copyOf(logs) : Set.of();
    }
}
