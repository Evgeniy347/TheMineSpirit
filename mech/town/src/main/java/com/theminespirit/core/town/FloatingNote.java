package com.theminespirit.core.town;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class FloatingNote {
    private static final int SHOWN = 4;

    private FloatingNote() {}

    public static String line(List<FloatingBlocks.Finding> findings) {
        Objects.requireNonNull(findings, "Находки висящего не могут быть null: пустой список — ничего не висит");
        Map<String, Integer> types = new LinkedHashMap<>();
        List<BlockCoord> cells = new ArrayList<>(SHOWN);
        for (FloatingBlocks.Finding finding : findings) {
            types.merge(finding.blockId(), finding.cells().size(), Integer::sum);
            if (cells.size() < SHOWN) {
                cells.add(finding.at());
            }
        }
        String kind = types.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
        return findings.size()
                + (kind.isEmpty() ? "" : " (" + kind + ")")
                + (cells.isEmpty() ? "" : ", первые " + cells);
    }
}
