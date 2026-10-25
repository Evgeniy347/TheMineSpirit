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
    private final int maxUnprotectVolume;

    public BlockProtection(IdFilter deny, IdFilter natural, int buildingThreshold, int maxUnprotectVolume) {
        this.deny =
                Objects.requireNonNull(deny, "Чёрный список не может быть null: без него защита блоков не работает");
        this.natural = Objects.requireNonNull(
                natural, "Список природных блоков не может быть null: по нему отличают постройки");
        if (deny.matchesAll() || natural.matchesAll()) {
            throw new IllegalArgumentException(
                    "Список deny или natural совпадает со всеми блоками: пустой или '*' список запретил бы всё "
                            + "либо признал бы всё природным, перечислите шаблоны блоков явно");
        }
        if (buildingThreshold < 1 || buildingThreshold > MAX_THRESHOLD) {
            throw new IllegalArgumentException(
                    "buildingThreshold " + buildingThreshold + " вне диапазона 1.." + MAX_THRESHOLD + ": среди "
                            + NEIGHBOUR_COUNT + " соседей не может быть больше искусственных блоков");
        }
        if (maxUnprotectVolume < 1) {
            throw new IllegalArgumentException("maxUnprotectVolume " + maxUnprotectVolume + " меньше 1: "
                    + "разрешённая область не может быть пустой");
        }
        this.buildingThreshold = buildingThreshold;
        this.maxUnprotectVolume = maxUnprotectVolume;
    }

    public sealed interface Decision {}

    public record Allowed() implements Decision {}

    public record Refused(PolicyId policy, String reason) implements Decision {
        public Refused {
            Objects.requireNonNull(policy, "Политика отказа не может быть null: по ней скрипт различает причины");
            Objects.requireNonNull(reason, "Причина отказа не может быть null: по ней строится итог задачи");
        }
    }

    // Участок поселения (INC-70, INC-71): блок, не поставленный игроком, — остаток построек поселения; чёрный список
    // (заборы, факелы, двери старой ограды и дома) его не держит — иначе старая ограда стоит вечно и ящик «защищён».
    // Непрочное по GatherPolicy (бедрок) и защищённая зона сервера — по-прежнему нельзя.
    public Decision checkSettlement(String blockId, boolean inProtectedZone) {
        Objects.requireNonNull(blockId, "Id блока не может быть null: без него неизвестно, что проверять");
        if (GatherPolicy.check(blockId) instanceof GatherPolicy.Refused refused) {
            return new Refused(PolicyId.PROTECTED_BLOCKS, refused.reason());
        }
        if (inProtectedZone) {
            return new Refused(
                    PolicyId.PROTECTED_ZONE,
                    "блок '" + blockId + "' лежит в защищённой зоне (серверный конфиг protectedZones)");
        }
        return new Allowed();
    }

    public Decision checkOwn(String blockId, boolean inProtectedZone) {
        Objects.requireNonNull(blockId, "Id блока не может быть null: без него неизвестно, что проверять");
        if (GatherPolicy.check(blockId) instanceof GatherPolicy.Refused refused) {
            return new Refused(PolicyId.PROTECTED_BLOCKS, refused.reason());
        }
        if (deny.matches(blockId)) {
            return new Refused(
                    PolicyId.PROTECTED_BLOCKS,
                    "блок '" + blockId + "' в чёрном списке защиты (protection.json → deny): "
                            + "его не ломают, чтобы не разрушить постройку или механизм");
        }
        if (inProtectedZone) {
            return new Refused(
                    PolicyId.PROTECTED_ZONE,
                    "блок '" + blockId + "' лежит в защищённой зоне (серверный конфиг protectedZones)");
        }
        return new Allowed();
    }

    public List<String> denyPatterns() {
        return deny.patterns();
    }

    public int buildingThreshold() {
        return buildingThreshold;
    }

    public int maxUnprotectVolume() {
        return maxUnprotectVolume;
    }

    public Decision check(String blockId, List<String> neighbours, boolean playerPlaced, boolean inProtectedZone) {
        return check(blockId, neighbours, new Placement(playerPlaced, inProtectedZone, false));
    }

    public Decision check(String blockId, List<String> neighbours, Placement placement) {
        boolean playerPlaced = placement.playerPlaced();
        boolean inProtectedZone = placement.inProtectedZone();
        boolean unprotectArea = placement.unprotectArea();
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
            return new Refused(
                    PolicyId.PROTECTED_BLOCKS,
                    "блок '" + blockId + "' в чёрном списке защиты (protection.json → deny): "
                            + "его не ломают, чтобы не разрушить постройку или механизм");
        }
        if (inProtectedZone) {
            return new Refused(
                    PolicyId.PROTECTED_ZONE,
                    "блок '" + blockId + "' лежит в защищённой зоне (серверный конфиг protectedZones)");
        }
        if (unprotectArea) {
            return new Allowed();
        }
        if (playerPlaced) {
            return new Refused(
                    PolicyId.PLAYER_BUILDINGS,
                    "блок '" + blockId + "' поставил игрок: чужие постройки дух не разбирает");
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
            return new Refused(
                    PolicyId.PLAYER_BUILDINGS,
                    "вокруг блока '" + blockId + "' " + artificial + " искусственных блоков из " + NEIGHBOUR_COUNT
                            + " (порог " + buildingThreshold + ", например '" + example + "'): это постройка игрока");
        }
        return new Allowed();
    }

    public Decision checkUse(List<String> neighbours, Placement placement) {
        boolean playerPlaced = placement.playerPlaced();
        boolean inProtectedZone = placement.inProtectedZone();
        boolean unprotectArea = placement.unprotectArea();
        Objects.requireNonNull(neighbours, "Список соседей не может быть null: по нему отличают постройку от природы");
        if (neighbours.size() != NEIGHBOUR_COUNT) {
            throw new IllegalArgumentException("Соседей " + neighbours.size() + ", ожидается " + NEIGHBOUR_COUNT
                    + ": 3×3×3 без самого блока; для незагруженной клетки передайте minecraft:air");
        }
        if (inProtectedZone) {
            return new Refused(
                    PolicyId.PROTECTED_ZONE, "механизм лежит в защищённой зоне (серверный конфиг protectedZones)");
        }
        if (unprotectArea) {
            return new Allowed();
        }
        if (playerPlaced) {
            return new Refused(
                    PolicyId.PLAYER_BUILDINGS, "блок поставил игрок: чужие механизмы дух не трогает без разрешения");
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
            return new Refused(
                    PolicyId.PLAYER_BUILDINGS,
                    "вокруг механизма " + artificial + " искусственных блоков из " + NEIGHBOUR_COUNT + " (порог "
                            + buildingThreshold + ", например '" + example + "'): это постройка игрока");
        }
        return new Allowed();
    }

    public record Placement(boolean playerPlaced, boolean inProtectedZone, boolean unprotectArea) {}
}
