package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildBox;
import com.theminespirit.core.mining.BreakSkipTally;
import java.util.List;

final class ClearSummary {
    private ClearSummary() {}

    record Refusals(int unbreakable, int outOfReach, int noSight, int chunkNotLoaded) {
        static Refusals of(BreakSkipTally skips) {
            return new Refusals(
                    skips.count(BreakSkipTally.Reason.UNBREAKABLE),
                    skips.count(BreakSkipTally.Reason.OUT_OF_REACH),
                    skips.count(BreakSkipTally.Reason.NO_SIGHT),
                    skips.count(BreakSkipTally.Reason.CHUNK_NOT_LOADED));
        }

        String text() {
            StringBuilder text = new StringBuilder();
            append(text, outOfReach, BreakSkipTally.Reason.OUT_OF_REACH.title());
            append(text, noSight, BreakSkipTally.Reason.NO_SIGHT.title());
            append(text, chunkNotLoaded, BreakSkipTally.Reason.CHUNK_NOT_LOADED.title());
            return text.toString();
        }

        private static void append(StringBuilder text, int count, String title) {
            if (count == 0) {
                return;
            }
            if (!text.isEmpty()) {
                text.append(", ");
            }
            text.append(title).append(": ").append(count);
        }
    }

    record Tally(
            int removed,
            int skipped,
            int unsafe,
            int noStand,
            int protectedSkipped,
            int foreignSkipped,
            int unreachable,
            Refusals refusals) {}

    static int volume(BuildBox box) {
        return (int) Math.min(box.volume(), Integer.MAX_VALUE);
    }

    static String of(BuildBox box, Tally tally, List<String> leftover) {
        String counts = "убрано " + tally.removed() + " из " + box.volume() + "; пропущено: " + tally.skipped()
                + " (небезопасных: " + tally.unsafe()
                + ", без стоянки: " + tally.noStand() + ", защищено: " + tally.protectedSkipped()
                + ", неразрушимых: " + tally.refusals().unbreakable()
                + ", занято другим духом: " + tally.foreignSkipped() + ")";
        if (!tally.refusals().text().isEmpty()) {
            counts += "; причины пропуска: " + tally.refusals().text();
        }
        if (tally.unreachable() > 0) {
            counts += "; недостижимо: " + tally.unreachable();
        }
        if (!leftover.isEmpty()) {
            counts += "; не взяты " + leftover;
        }
        return counts;
    }
}
