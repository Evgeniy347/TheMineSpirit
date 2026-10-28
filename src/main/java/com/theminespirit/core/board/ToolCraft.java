package com.theminespirit.core.board;

import java.util.Collection;
import java.util.Objects;

public final class ToolCraft {
    private ToolCraft() {}

    public static boolean fulfilled(BoardCase boardCase, Collection<String> items) {
        Objects.requireNonNull(boardCase, "Кейс крафта не может быть null: по нему известен нужный инструмент");
        Objects.requireNonNull(items, "Инвентарь духа не может быть null: пустой — пустой набор");
        if (!ToolSet.isTool(boardCase)) {
            return true;
        }
        String wanted = ToolSet.itemId(boardCase);
        String bare = bare(wanted);
        return items.stream().anyMatch(item -> item.equals(wanted) || item.equals(bare));
    }

    private static String bare(String item) {
        int at = item.indexOf(':');
        return at < 0 ? item : item.substring(at + 1);
    }
}
