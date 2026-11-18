package com.theminespirit.core.forestry;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public final class TreeSight {
    public record Stand(String species, BlockCoord base) {
        public Stand {
            Objects.requireNonNull(species, "Порода найденного дерева не может быть null: по ней метка мира");
            Objects.requireNonNull(base, "Ствол найденного дерева не может быть null: по нему ставится цель");
        }
    }

    private TreeSight() {}

    public static List<Stand> detect(
            Map<String, ? extends Collection<BlockCoord>> logCells, Function<String, TreeView> views) {
        return detect(logCells, views, cell -> false);
    }

    public static List<Stand> detect(
            Map<String, ? extends Collection<BlockCoord>> logCells,
            Function<String, TreeView> views,
            Predicate<BlockCoord> orchardCare) {
        Objects.requireNonNull(logCells, "Клетки брёвен не могут быть null: без них нечего разбирать");
        Objects.requireNonNull(views, "Взгляд по породе не может быть null: без него дерево не отличить от постройки");
        Objects.requireNonNull(
                orchardCare, "Признак сада не может быть null: без него не ясно, где крона через разрыв");
        List<Stand> found = new ArrayList<>();
        Set<BlockCoord> known = new HashSet<>();
        for (Map.Entry<String, ? extends Collection<BlockCoord>> entry : logCells.entrySet()) {
            TreeView view = views.apply(entry.getKey());
            for (BlockCoord cell : entry.getValue()) {
                if (known.contains(cell) || !view.isLog(cell)) {
                    known.add(cell);
                    continue;
                }
                TreeDetection detection = TreeShape.detect(cell, view, true, orchardCare.test(cell));
                if (detection instanceof Tree tree) {
                    found.add(new Stand(entry.getKey(), tree.base()));
                    known.addAll(tree.logs());
                } else if (detection instanceof NotATree rejected) {
                    known.addAll(rejected.logs());
                } else {
                    known.add(cell);
                }
            }
        }
        return List.copyOf(found);
    }
}
