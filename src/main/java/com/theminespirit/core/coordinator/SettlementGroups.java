package com.theminespirit.core.coordinator;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.Point;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class SettlementGroups {
    private SettlementGroups() {
    }

    public static SettlementKey keyOf(Optional<BlockCoord> home, UUID spirit) {
        if (home == null) {
            throw new IllegalArgumentException(
                    "home не может быть null: отсутствие дома — Optional.empty(), иначе дух потеряет поселение");
        }
        if (spirit == null) {
            throw new IllegalArgumentException(
                    "spirit не может быть null: одиночная группа привязана к духу, иначе её не отличить");
        }
        return home.map(SettlementKey::homed).orElseGet(() -> SettlementKey.lone(spirit));
    }

    public static Map<SettlementKey, List<SpiritSnapshot>> partition(List<SpiritSnapshot> spirits,
            Map<UUID, Optional<BlockCoord>> homes) {
        if (spirits == null) {
            throw new IllegalArgumentException(
                    "spirits не может быть null: пустое поселение — пустой список, иначе план соберут из ничего");
        }
        if (homes == null) {
            throw new IllegalArgumentException(
                    "homes не может быть null: дома духов задают ключи групп, иначе поселения склеятся");
        }
        Map<SettlementKey, List<SpiritSnapshot>> groups = new LinkedHashMap<>();
        for (SpiritSnapshot spirit : spirits) {
            SettlementKey key = keyOf(homes.getOrDefault(spirit.id(), Optional.empty()), spirit.id());
            groups.computeIfAbsent(key, group -> new ArrayList<>()).add(spirit);
        }
        return groups;
    }

    public static Optional<Point> defendedPoint(SettlementKey key) {
        if (key == null) {
            throw new IllegalArgumentException(
                    "key не может быть null: точка обороны берётся из ключа своей группы, иначе защищается чужой дом");
        }
        if (key instanceof SettlementKey.Homed homed) {
            BlockCoord home = homed.home();
            return Optional.of(new Point(home.x() + 0.5, home.y(), home.z() + 0.5));
        }
        return Optional.empty();
    }
}
