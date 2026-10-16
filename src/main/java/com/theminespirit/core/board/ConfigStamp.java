package com.theminespirit.core.board;

import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.reload.TableHolder;
import java.util.Objects;

/**
 * Версия конфигурации для журналов: хэш таблицы board на момент записи. По нему отличают
 * правила, по которым жило задание или решение, и видят, что /reload сменил таблицу.
 */
public final class ConfigStamp {
    private ConfigStamp() {
    }

    public static String current() {
        return DataTables.INSTANCE.board().status().hash();
    }

    public static String of(TableHolder<?> holder) {
        Objects.requireNonNull(holder, "Держатель таблицы не может быть null: без него неизвестен хэш");
        return holder.status().hash();
    }
}
