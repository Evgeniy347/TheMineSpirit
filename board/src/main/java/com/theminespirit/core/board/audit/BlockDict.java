package com.theminespirit.core.board.audit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BlockDict {
    public static final short UNKNOWN = 0;
    public static final short MAX_CODES = Short.MAX_VALUE;

    private final List<String> ids = new ArrayList<>();
    private final Map<String, Short> codes = new HashMap<>();

    public BlockDict() {
        ids.add("");
    }

    public short intern(String id) {
        Objects.requireNonNull(id, "Id блока не может быть null: неизвестный блок — UNKNOWN, а не null");
        Short known = codes.get(id);
        if (known != null) {
            return known;
        }
        if (ids.size() > MAX_CODES) {
            throw new IllegalStateException("Словарь блока переполнен на " + ids.size() + " значениях: в чанке "
                    + "бывает десятки id, сотни значат, что id не нормализуют и кэш разрастётся");
        }
        short code = (short) ids.size();
        ids.add(id);
        codes.put(id, code);
        return code;
    }

    public String at(short code) {
        if (code < 0 || code >= ids.size()) {
            throw new IllegalArgumentException("Код блока " + code + " вне словаря размером " + ids.size()
                    + ": кэш и словарь разошлись, клетка прочитается чужим блоком");
        }
        return ids.get(code);
    }

    public boolean known(short code) {
        return code != UNKNOWN && code >= 0 && code < ids.size();
    }
}
