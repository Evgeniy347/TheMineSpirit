package com.theminespirit.core.board.graph;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

public record GraphUndo(
        int base,
        int inside,
        int street,
        double perHeight,
        int heightCap,
        double perCell,
        int cellCap,
        int farDistance,
        double farShare,
        double ownerFactor,
        int buriedDepth,
        double buriedShare) {
    public static final int DEFAULT_BASE = 120;
    public static final GraphUndo DEFAULTS =
            new GraphUndo(DEFAULT_BASE, 120, 20, 1.0, 30, 6.0, 60, 256, 0.8, 0.8, 8, 0.6);

    public GraphUndo {
        if (base < 1) {
            throw new IllegalArgumentException("Базовый вес отката " + base + " меньше 1: задание не выйдет из нуля");
        }
        if (inside < 0 || street < 0 || heightCap < 0 || cellCap < 0 || farDistance < 1 || buriedDepth < 1) {
            throw new IllegalArgumentException("Поправки отката (поселение " + inside + ", улица " + street
                    + ", пределы " + heightCap + "/" + cellCap + ", даль " + farDistance + ", глубина "
                    + buriedDepth + ") не отрицательны, даль и глубина больше нуля");
        }
        if (perHeight < 0
                || perCell < 0
                || farShare < 0
                || farShare > 1
                || ownerFactor < 0
                || ownerFactor > 1
                || buriedShare < 0
                || buriedShare > 1) {
            throw new IllegalArgumentException("Доли отката (высота " + perHeight + ", клетки " + perCell + ", даль "
                    + farShare + ", владелец " + ownerFactor + ", глубина " + buriedShare
                    + ") лежат в 0..1, кроме высоты и клеток");
        }
    }

    static GraphUndo parse(JsonNode root) {
        JsonNode undo = root.path("undo");
        if (undo.isMissingNode() || !undo.isObject()) {
            return DEFAULTS;
        }
        return new GraphUndo(
                undo.path("base").asInt(DEFAULTS.base()),
                undo.path("inside").asInt(DEFAULTS.inside()),
                undo.path("street").asInt(DEFAULTS.street()),
                undo.path("perHeight").asDouble(DEFAULTS.perHeight()),
                undo.path("heightCap").asInt(DEFAULTS.heightCap()),
                undo.path("perCell").asDouble(DEFAULTS.perCell()),
                undo.path("cellCap").asInt(DEFAULTS.cellCap()),
                undo.path("farDistance").asInt(DEFAULTS.farDistance()),
                undo.path("farShare").asDouble(DEFAULTS.farShare()),
                undo.path("owner").asDouble(DEFAULTS.ownerFactor()),
                undo.path("buriedDepth").asInt(DEFAULTS.buriedDepth()),
                undo.path("buriedShare").asDouble(DEFAULTS.buriedShare()));
    }

    public int weight(GraphSiteFacts.Lease lease, boolean ownerAlive, int maxPriority) {
        Objects.requireNonNull(lease, "Аренда не может быть null: откат считается по её клеткам");
        double weight = base;
        if (lease.inside()) {
            weight += inside;
        }
        if (lease.street()) {
            weight += street;
        }
        weight += Math.min(heightCap, perHeight * lease.height());
        weight += Math.min(cellCap, perCell * lease.cells());
        double far = Math.min(1.0, (double) lease.distance() / farDistance);
        weight -= base * farShare * far;
        if (!lease.inside() && !lease.street()) {
            double buried = Math.min(1.0, (double) lease.depth() / buriedDepth);
            weight -= base * buriedShare * buried;
        }
        if (ownerAlive && lease.owner() != null) {
            weight *= ownerFactor;
        }
        return Math.max(1, (int) Math.round(weight));
    }
}
