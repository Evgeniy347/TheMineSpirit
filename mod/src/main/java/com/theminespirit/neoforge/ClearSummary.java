package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildBox;
import com.theminespirit.core.mining.BreakSkipTally;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;

final class ClearSummary {
    private ClearSummary() {}

    record Leftover(BlockCoord at, String block, String reason, int repeats) {
        String text() {
            String repeat = repeats > 1 ? ", повтор " + repeats : "";
            return "(" + at.x() + ", " + at.y() + ", " + at.z() + ") " + block + " — " + reason + repeat;
        }
    }

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
            int excluded,
            Refusals refusals) {}

    static int volume(BuildBox box) {
        return (int) Math.min(box.volume(), Integer.MAX_VALUE);
    }

    static String of(ClearAreaSession session) {
        return of(
                session.box,
                new Tally(
                        session.removed,
                        session.skippedTotal(),
                        session.unsafe,
                        session.noStand,
                        session.protectedSkipped,
                        session.foreignSkipped,
                        session.unreachable,
                        session.excludedSkipped,
                        Refusals.of(session.breakSkips)),
                session.leftover);
    }

    static String of(BuildBox box, Tally tally, List<Leftover> leftover) {
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
        if (tally.excluded() > 0) {
            counts += "; исключено недостижимых: " + tally.excluded();
        }
        if (!leftover.isEmpty()) {
            counts += "; не взяты " + leftover.stream().map(Leftover::text).toList();
        }
        return counts;
    }
}
