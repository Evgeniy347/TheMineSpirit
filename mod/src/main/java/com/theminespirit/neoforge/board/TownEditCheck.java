package com.theminespirit.neoforge.board;

import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.PathLine;
import com.theminespirit.core.town.Placement;
import com.theminespirit.core.town.TownEntry;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.TownRules;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class TownEditCheck {
    private TownEditCheck() {}

    static void validate(
            TownPlan plan,
            TownRules rules,
            List<Placement> placements,
            List<PathLine> paths,
            List<ColumnMask.Cell> posts,
            List<Integer> gates,
            List<String> errors) {
        int gap = rules.spacing().gap();
        Set<String> ids = new HashSet<>();
        for (Placement place : placements) {
            if (!ids.add(place.id())) {
                errors.add("участок '" + place.id() + "' объявлен дважды");
            }
            TownEntry entry = rules.entry(place.id()).orElse(null);
            if (entry != null && (place.width() != entry.width() || place.depth() != entry.depth())) {
                errors.add("размер участка '" + place.id() + "' закреплён: " + entry.width() + "×" + entry.depth()
                        + ", а прислан " + place.width() + "×" + place.depth());
            }
            for (ColumnMask.Cell cell : place.mask().cells()) {
                if (!plan.region().contains(cell.x(), cell.z())) {
                    errors.add("участок '" + place.id() + "' вне границы поселения в " + cell.x() + "," + cell.z());
                    break;
                }
                if (plan.streets().contains(cell.x(), cell.z())) {
                    errors.add("участок '" + place.id() + "' налезает на улицу в " + cell.x() + "," + cell.z());
                    break;
                }
            }
            for (Placement other : placements) {
                if (!other.id().equals(place.id())
                        && other.id().compareTo(place.id()) < 0
                        && other.overlaps(place, gap)) {
                    errors.add("участок '" + place.id() + "' накладывается на '" + other.id() + "'");
                }
            }
        }
        for (PathLine path : paths) {
            for (ColumnMask.Cell cell : path.cells()) {
                if (!plan.region().contains(cell.x(), cell.z())) {
                    errors.add("тропинка '" + path.id() + "' вне границы поселения в " + cell.x() + "," + cell.z());
                    break;
                }
                if (placements.stream().anyMatch(place -> place.covers(cell.x(), cell.z()))) {
                    errors.add("тропинка '" + path.id() + "' идёт по участку в " + cell.x() + "," + cell.z());
                    break;
                }
            }
        }
        for (int gate : gates) {
            if (gate < 0 || gate >= posts.size()) {
                errors.add("ворота №" + gate + " вне линии ограды");
            }
        }
    }

    static int indexOf(List<ColumnMask.Cell> posts, int x, int z) {
        for (int index = 0; index < posts.size(); index++) {
            ColumnMask.Cell cell = posts.get(index);
            if (cell.x() == x && cell.z() == z) {
                return index;
            }
        }
        return -1;
    }
}
