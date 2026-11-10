package com.theminespirit.core.board;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class BlockedReason {
    private BlockedReason() {}

    static List<String> of(
            int failures,
            @Nullable GraphBlock block,
            Map<String, Integer> missing,
            @Nullable String last,
            List<String> history) {
        List<String> reason = new ArrayList<>();
        reason.add("failures=" + failures);
        if (block != null) {
            reason.add("need=" + block.need());
        }
        missing.forEach((item, count) -> reason.add("missing=" + item + ":" + count));
        if (last != null) {
            reason.add("last=" + last);
        }
        reason.add("history=" + String.join("; ", history));
        return List.copyOf(reason);
    }
}
