package com.theminespirit.core.town;

import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.Schematic;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record WallFragment(
        String scheme, int sizeX, int sizeZ, List<ColumnMask.Cell> cells, ColumnMask.@Nullable Cell gate) {
    private static final Comparator<ColumnMask.Cell> ORDER =
            Comparator.comparingInt(ColumnMask.Cell::z).thenComparingInt(ColumnMask.Cell::x);

    public WallFragment {
        Objects.requireNonNull(scheme, "Схема фрагмента не может быть null: фрагмент строится по ней");
        cells = List.copyOf(Objects.requireNonNull(cells, "Клетки фрагмента не могут быть null"));
        if (cells.isEmpty()) {
            throw new IllegalArgumentException(
                    "Фрагмент '" + scheme + "' без клеток в нижнем слое: линии он не закроет");
        }
        if (gate != null && !cells.contains(gate)) {
            throw new IllegalArgumentException("Калитка фрагмента '" + scheme + "' вне его клеток");
        }
    }

    public static WallFragment of(String scheme, Schematic schematic) {
        Objects.requireNonNull(schematic, "Схема фрагмента '" + scheme + "' не загружена");
        List<ColumnMask.Cell> cells = new ArrayList<>();
        ColumnMask.Cell gate = null;
        for (Schematic.Entry entry : schematic.blocks()) {
            if (entry.dy() != 0) {
                continue;
            }
            ColumnMask.Cell cell = new ColumnMask.Cell(entry.dx(), entry.dz());
            cells.add(cell);
            if (entry.block().endsWith("_gate") || entry.block().endsWith("_door")) {
                gate = cell;
            }
        }
        return new WallFragment(scheme, schematic.sizeX(), schematic.sizeZ(), cells, gate);
    }

    public Shape shape(BuildRotation rotation) {
        List<ColumnMask.Cell> turned = new ArrayList<>(cells.size());
        for (ColumnMask.Cell cell : cells) {
            turned.add(turn(rotation, cell));
        }
        int minX = turned.stream().mapToInt(ColumnMask.Cell::x).min().orElseThrow();
        int minZ = turned.stream().mapToInt(ColumnMask.Cell::z).min().orElseThrow();
        return new Shape(key(turned), minX, minZ, gate == null ? null : turn(rotation, gate));
    }

    public static String key(List<ColumnMask.Cell> cells) {
        int minX = cells.stream().mapToInt(ColumnMask.Cell::x).min().orElseThrow();
        int minZ = cells.stream().mapToInt(ColumnMask.Cell::z).min().orElseThrow();
        StringBuilder text = new StringBuilder();
        cells.stream()
                .map(cell -> new ColumnMask.Cell(cell.x() - minX, cell.z() - minZ))
                .sorted(ORDER)
                .forEach(cell ->
                        text.append(cell.x()).append(',').append(cell.z()).append(';'));
        return text.toString();
    }

    private ColumnMask.Cell turn(BuildRotation rotation, ColumnMask.Cell cell) {
        BuildRotation.Cell turned = rotation.rotate(cell.x(), cell.z(), sizeX, sizeZ);
        return new ColumnMask.Cell(turned.x(), turned.z());
    }

    public record Shape(String key, int minX, int minZ, ColumnMask.@Nullable Cell gate) {}
}
