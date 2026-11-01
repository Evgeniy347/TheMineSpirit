package com.theminespirit.neoforge.intellect;

import com.mojang.logging.LogUtils;
import java.util.List;
import org.slf4j.Logger;

final class DecisionLog {
    private static final Logger LOGGER = LogUtils.getLogger();

    private List<String> refusals = List.of();
    private List<String> notes = List.of();

    void tick(List<String> nextRefusals, List<String> nextNotes) {
        if (!nextRefusals.equals(refusals)) {
            refusals = List.copyOf(nextRefusals);
            nextRefusals.forEach(reason -> LOGGER.info("Внешняя работа не назначена: {}", reason));
        }
        if (!nextNotes.equals(notes)) {
            notes = List.copyOf(nextNotes);
            nextNotes.forEach(note -> LOGGER.info("Граница поселения: {}", note));
        }
    }
}
