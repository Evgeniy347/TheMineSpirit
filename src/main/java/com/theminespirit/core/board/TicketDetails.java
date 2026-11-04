package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class TicketDetails {
    private TicketDetails() {}

    static List<String> of(TicketTarget target) {
        List<String> details = new ArrayList<>();
        details.add(BoardActivities.TARGET_KEY + coords(target.target()));
        if (target.schemeFile() != null) {
            details.add("scheme=" + target.schemeFile());
        }
        if (target.building() != null) {
            details.add("building=" + target.building());
        }
        if (target.wood() != null) {
            details.add("wood=" + target.wood());
        }
        if (target.fenceBlock() != null) {
            details.add("fence=" + target.fenceBlock());
        }
        if (target.lureItem() != null) {
            details.add("lure=" + target.lureItem());
        }
        if (target.supplyItem() != null) {
            details.add("supply=" + target.supplyItem() + "×" + target.supplyCount());
        }
        if (target.craftItem() != null) {
            details.add("craft=" + target.craftItem() + "×" + target.count());
        }
        if (target.species() != null) {
            details.add("species=" + target.species().name().toLowerCase(Locale.ROOT));
        }
        if (target.from() != null) {
            details.add("from=" + coords(target.from()));
        }
        if (target.to() != null) {
            details.add("to=" + coords(target.to()));
        }
        if (target.deposit() != null) {
            details.add("deposit=" + coords(target.deposit()));
        }
        if (target.toolCount() > 0) {
            details.add("tools=" + target.toolCount());
        }
        if (target.craterBlocks() > 0) {
            details.add("crater=" + target.craterBlocks());
        }
        if (!target.dropItems().isEmpty()) {
            details.add("drops=" + String.join(",", target.dropItems()));
        }
        return List.copyOf(details);
    }

    private static String coords(BlockCoord at) {
        return at.x() + "," + at.y() + "," + at.z();
    }
}
