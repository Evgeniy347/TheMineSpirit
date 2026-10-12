package com.theminespirit.core.policy;

import com.theminespirit.core.mining.GatherPolicy;
import com.theminespirit.core.observation.IdFilter;
import java.util.List;
import java.util.Objects;

public final class BlockProtection {
    public static final int NEIGHBOUR_COUNT = 26;
    public static final int MAX_THRESHOLD = NEIGHBOUR_COUNT;

    private final IdFilter deny;
    private final IdFilter natural;
    private final int buildingThreshold;

    public BlockProtection(IdFilter deny, IdFilter natural, int buildingThreshold) {
        this.deny = Objects.requireNonNull(deny, "Чёрный список не может быть null: без него защита блоков не работает");
        this.natural = Objects.requireNonNull(natural, "Список природных блоков не может быть null: по нему отличают постройки");
        if (deny.matchesAll() || natural.matchesAll()) {
            throw new IllegalArgumentException("Список deny или natural совпадает со всеми блоками: пустой или '*' список запретил бы всё "
                    + "либо признал бы всё природным, перечислите шаблоны блоков явно");
        }
        if (buildingThreshold < 1 || buildingThreshold > MAX_THRESHOLD) {
            throw new IllegalArgumentException("buildingThreshold " + buildingThreshold + " вне диапазона 1.." + MAX_THRESHOLD
                    + ": среди " + NEIGHBOUR_COUNT + " соседей не может быть больше искусственных блоков");
        }
        this.buildingThreshold = buildingThreshold;
    }

    public sealed interface Decision {
    }

    public record Allowed() implements Decision {
    }

    public record Refused(PolicyId policy, String reason) implements Decision {
        public Refused {
            Objects.requireNonNull(policy, "Политика отказа не может быть null: по ней скрипт различает причины");
            Objects.requireNonNull(reason, "Причина отказа не может быть null: по ней строится итог задачи");
        }
    }

    public List<String> denyPatterns() {
        return deny.patterns();
    }

    public int buildingThreshold() {
        return buildingThreshold;
    }

    public Decision check(String blockId, List<String> neighbours, boolean playerPlaced, boolean inProtectedZone) {
        Objects.requireNonNull(blockId, "Id блока не может быть null: без него неизвестно, что проверять");
        Objects.requireNonNull(neighbours, "Список соседей не может быть null: по нему отличают постройку от природы");
        if (neighbours.size() != NEIGHBOUR_COUNT) {
            throw new IllegalArgumentException("Соседей " + neighbours.size() + ", ожидается " + NEIGHBOUR_COUNT
                    + ": 3×3×3 без самого блока; для незагруженной клетки передайте minecraft:air");
        }
        if (GatherPolicy.check(blockId) instanceof GatherPolicy.Refused refused) {
            return new Refused(PolicyId.PROTECTED_BLOCKS, refused.reason());
        }
        if (deny.matches(blockId)) {
            return new Refused(PolicyId.PROTECTED_BLOCKS, "блок '" + blockId + "' в чёрном списке защиты (protection.json → deny): "
                    + "его не ломают, чтобы не разрушить постройку или механизм");
        }
        if (inProtectedZone) {
            return new Refused(PolicyId.PROTECTED_ZONE, "блок '" + blockId + "' лежит в защищённой зоне (серверный конфиг protectedZones)");
        }
        if (playerPlaced) {
            return new Refused(PolicyId.PLAYER_BUILDINGS, "блок '" + blockId + "' поставил игрок: чужие постройки дух не разбирает");
        }
        int artificial = 0;
        String example = null;
        for (String neighbour : neighbours) {
            if (!natural.matches(neighbour)) {
                artificial++;
                example = example == null ? neighbour : example;
            }
        }
        if (artificial >= buildingThreshold) {
            return new Refused(PolicyId.PLAYER_BUILDINGS, "вокруг блока '" + blockId + "' " + artificial + " искусственных блоков из "
                    + NEIGHBOUR_COUNT + " (порог " + buildingThreshold + ", например '" + example + "'): это постройка игрока");
        }
        return new Allowed();
    }
}
