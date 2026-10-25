package com.theminespirit.core.board;

import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.reload.TableHolder;
import java.util.Objects;

public final class ConfigStamp {
    private ConfigStamp() {}

    public static String current() {
        return DataTables.INSTANCE.board().status().hash();
    }

    public static String of(TableHolder<?> holder) {
        Objects.requireNonNull(holder, "Держатель таблицы не может быть null: без него неизвестен хэш");
        return holder.status().hash();
    }
}
