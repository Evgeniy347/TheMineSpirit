package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildBox;
import java.util.List;

final class ClearSummary {
    private ClearSummary() {}

    record Tally(
            int removed,
            int skipped,
            int unsafe,
            int noStand,
            int protectedSkipped,
            int unbreakable,
            int foreignSkipped,
            int unreachable) {}

    static int volume(BuildBox box) {
        return (int) Math.min(box.volume(), Integer.MAX_VALUE);
    }

    static String of(BuildBox box, Tally tally, List<String> leftover) {
        String counts = "убрано " + tally.removed() + " из " + box.volume() + "; пропущено: " + tally.skipped()
                + " (небезопасных: " + tally.unsafe()
                + ", без стоянки: " + tally.noStand() + ", защищено: " + tally.protectedSkipped()
                + ", неразрушимых: " + tally.unbreakable()
                + ", занято другим духом: " + tally.foreignSkipped() + ")";
        if (tally.unreachable() > 0) {
            counts += "; недостижимо: " + tally.unreachable();
        }
        if (!leftover.isEmpty()) {
            counts += "; не взяты " + leftover;
        }
        return counts;
    }
}
