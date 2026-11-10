package com.theminespirit.neoforge.lease;

import com.theminespirit.neoforge.BlockStates;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

public final class LeaseStates {
    private LeaseStates() {}

    public static String text(BlockState state) {
        Objects.requireNonNull(state, "Состояние блока не может быть null: записывать нечего");
        StringBuilder text = new StringBuilder(BlockStates.idOf(state));
        Map<String, String> properties = new LinkedHashMap<>();
        for (Property<?> property : state.getProperties()) {
            properties.put(property.getName(), value(state, property));
        }
        if (!properties.isEmpty()) {
            text.append('[');
            boolean first = true;
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                if (!first) {
                    text.append(',');
                }
                first = false;
                text.append(entry.getKey()).append('=').append(entry.getValue());
            }
            text.append(']');
        }
        return text.toString();
    }

    public static BlockState state(String text) {
        Objects.requireNonNull(text, "Строка состояния не может быть null: без неё блок не вернуть");
        int open = text.indexOf('[');
        if (open < 0) {
            return BlockStates.stateFor(text);
        }
        String id = text.substring(0, open);
        String tail = text.substring(open + 1, text.endsWith("]") ? text.length() - 1 : text.length());
        Map<String, String> properties = new LinkedHashMap<>();
        int start = 0;
        while (start <= tail.length()) {
            int comma = tail.indexOf(',', start);
            String pair = comma < 0 ? tail.substring(start) : tail.substring(start, comma);
            int equals = pair.indexOf('=');
            if (equals <= 0) {
                throw new IllegalArgumentException("Свойство состояния '" + pair + "' в '" + text
                        + "' не в формате имя=значение: откат поставил бы блок не так");
            }
            properties.put(
                    pair.substring(0, equals).strip(),
                    pair.substring(equals + 1).strip());
            if (comma < 0) {
                break;
            }
            start = comma + 1;
        }
        return BlockStates.stateFor(id, properties);
    }

    public static void apply(ServerLevel level, BlockPos pos, String text) {
        Objects.requireNonNull(level, "Уровень не может быть null: состояние блока живёт в мире");
        Objects.requireNonNull(pos, "Клетка не может быть null: без неё неизвестно, что вернуть");
        BlockState state = state(text);
        if (!level.getBlockState(pos).equals(state)) {
            level.setBlock(pos, state, 3);
        }
    }

    private static <T extends Comparable<T>> String value(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }
}
