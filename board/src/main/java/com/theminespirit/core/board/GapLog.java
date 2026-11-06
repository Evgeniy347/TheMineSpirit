package com.theminespirit.core.board;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class GapLog {
    private static final Map<String, String> LAST = new HashMap<>();

    private GapLog() {}

    public static boolean changed(BoardCase boardCase, List<String> missing) {
        Objects.requireNonNull(boardCase, "Кейс этапа не может быть null: по нему помнят прошлый список");
        Objects.requireNonNull(missing, "Список недостающих блоков не может быть null: пустой задают пустым");
        String signature = String.join(";", missing);
        return !signature.equals(LAST.put(boardCase.name(), signature));
    }
}
