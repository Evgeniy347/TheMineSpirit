package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Подземная схема в мире: сегмент файла, повторённый по {@code repeat}, и повёрнутый вокруг первого
 * сегмента. Первый сегмент стоит там же, где стоял бы без повтора: якорь стройки — угол первого сегмента, а не угол
 * развёрнутой схемы, поэтому вход не зависит от того, до какой глубины спуск.
 */
public record MineLayout(
        List<BlockCoord> floors, List<BlockCoord> air, List<Schematic.PlacedEntry> optional, BlockCoord entrance) {
    public MineLayout {
        floors = List.copyOf(floors);
        air = List.copyOf(air);
        optional = List.copyOf(optional);
        Objects.requireNonNull(entrance, "Вход не может быть null: проходка начинается от него");
    }

    /** Переопределение повтора стройкой: целевой y спуска или число сегментов хода; null — как в файле. */
    public record Extent(@Nullable Integer untilY, @Nullable Integer segments) {
        public static final Extent NONE = new Extent(null, null);
    }

    public static MineLayout place(
            Schematic segment, BlockCoord entryCell, BlockCoord origin, BuildRotation rotation, Extent override) {
        Objects.requireNonNull(segment, "Схема не может быть null: в подземной стройке нечего копать");
        Objects.requireNonNull(entryCell, "Вход схемы не может быть null: проходка начинается от него");
        Objects.requireNonNull(origin, "Якорь не может быть null: без него неизвестно, где копать");
        Objects.requireNonNull(rotation, "Поворот не может быть null: NONE — без поворота");
        Objects.requireNonNull(override, "Переопределение не может быть null: без него — Extent.NONE");
        Schematic.Repeat repeat = repeat(segment.repeat(), override);
        int copies = repeat == null ? 1 : repeat.copies(origin.y(), baseMinY(segment));
        Set<BlockCoord> floors = new LinkedHashSet<>();
        Set<BlockCoord> air = new LinkedHashSet<>();
        List<Schematic.PlacedEntry> optional = new ArrayList<>();
        for (int copy = 0; copy < copies; copy++) {
            int sx = repeat == null ? 0 : repeat.dx() * copy;
            int sy = repeat == null ? 0 : repeat.dy() * copy;
            int sz = repeat == null ? 0 : repeat.dz() * copy;
            for (Schematic.Entry entry : segment.blocks()) {
                BlockCoord pos = at(segment, origin, rotation, entry.dx() + sx, entry.dy() + sy, entry.dz() + sz);
                if (entry.optional()) {
                    optional.add(new Schematic.PlacedEntry(pos, entry.block(), entry.state(), entry.text(), true));
                } else if (SchematicBlocks.isFloor(entry.block())) {
                    floors.add(pos);
                }
            }
            for (Schematic.Cell cell : segment.air()) {
                air.add(at(segment, origin, rotation, cell.dx() + sx, cell.dy() + sy, cell.dz() + sz));
            }
        }
        air.removeAll(floors);
        BlockCoord entrance = at(segment, origin, rotation, entryCell.x(), entryCell.y(), entryCell.z());
        return new MineLayout(List.copyOf(floors), List.copyOf(air), optional, entrance);
    }

    private static Schematic.@Nullable Repeat repeat(Schematic.@Nullable Repeat declared, Extent override) {
        if (declared == null) {
            return null;
        }
        if (override.untilY() != null) {
            return new Schematic.Repeat(
                    declared.dx(), declared.dy(), declared.dz(), new Schematic.Repeat.TargetY(override.untilY()));
        }
        if (override.segments() != null) {
            return new Schematic.Repeat(
                    declared.dx(), declared.dy(), declared.dz(), new Schematic.Repeat.Times(override.segments()));
        }
        return declared;
    }

    private static BlockCoord at(Schematic segment, BlockCoord origin, BuildRotation rotation, int dx, int dy, int dz) {
        BuildRotation.Cell cell = rotation.rotate(dx, dz, segment.sizeX(), segment.sizeZ());
        return new BlockCoord(origin.x() + cell.x(), origin.y() + dy, origin.z() + cell.z());
    }

    private static int baseMinY(Schematic segment) {
        int min = Integer.MAX_VALUE;
        for (Schematic.Entry entry : segment.blocks()) {
            min = Math.min(min, entry.dy());
        }
        for (Schematic.Cell cell : segment.air()) {
            min = Math.min(min, cell.dy());
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }
}
