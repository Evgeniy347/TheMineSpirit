package com.theminespirit.core.building;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class SchematicPart {
    public static final String SEPARATOR = "#";
    public static final int PARTS_MAX = 16;

    public record Ref(String file, int part, int parts) {
        public Ref {
            Objects.requireNonNull(file, "Имя схемы части не может быть null: без него неизвестно, что делить");
            if (parts < 1 || parts > PARTS_MAX || part < 1 || part > parts) {
                throw new IllegalArgumentException("Часть " + part + " из " + parts + " схемы '" + file + "' вне 1.."
                        + PARTS_MAX + ": номер части — от 1 до числа частей");
            }
        }
    }

    private SchematicPart() {}

    public static String name(String file, int part, int parts) {
        Ref ref = new Ref(file, part, parts);
        return ref.file() + SEPARATOR + ref.part() + "/" + ref.parts();
    }

    public static Optional<Ref> parse(String name) {
        Objects.requireNonNull(name, "Имя схемы не может быть null: без него неизвестно, что грузить");
        int hash = name.lastIndexOf(SEPARATOR);
        int slash = name.lastIndexOf('/');
        if (hash <= 0 || slash < hash) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Ref(
                    name.substring(0, hash),
                    Integer.parseInt(name.substring(hash + 1, slash)),
                    Integer.parseInt(name.substring(slash + 1))));
        } catch (IllegalArgumentException notAPart) {
            return Optional.empty();
        }
    }

    public static Schematic of(Schematic whole, int part, int parts) {
        Objects.requireNonNull(whole, "Схема не может быть null: делить нечего");
        Ref ref = new Ref("schematic", part, parts);
        Set<Long> own = sector(whole, ref.part(), ref.parts());
        List<Schematic.Entry> blocks = new ArrayList<>();
        for (Schematic.Entry entry : whole.blocks()) {
            if (own.contains(column(entry.dx(), entry.dz()))) {
                blocks.add(entry);
            }
        }
        List<Schematic.Cell> air = new ArrayList<>();
        for (Schematic.Cell cell : whole.air()) {
            if (own.contains(column(cell.dx(), cell.dz()))) {
                air.add(cell);
            }
        }
        return new Schematic(whole.sizeX(), whole.sizeY(), whole.sizeZ(), blocks, air, whole.repeat());
    }

    private static Set<Long> sector(Schematic whole, int part, int parts) {
        Set<Long> seen = new LinkedHashSet<>();
        List<int[]> columns = new ArrayList<>();
        for (Schematic.Entry entry : whole.blocks()) {
            if (seen.add(column(entry.dx(), entry.dz()))) {
                columns.add(new int[] {entry.dx(), entry.dz()});
            }
        }
        for (Schematic.Cell cell : whole.air()) {
            if (seen.add(column(cell.dx(), cell.dz()))) {
                columns.add(new int[] {cell.dx(), cell.dz()});
            }
        }
        double cx = (whole.sizeX() - 1) / 2.0;
        double cz = (whole.sizeZ() - 1) / 2.0;
        columns.sort(Comparator.<int[]>comparingDouble(c -> Math.atan2(c[1] - cz, c[0] - cx))
                .thenComparingInt(c -> c[0])
                .thenComparingInt(c -> c[1]));
        int from = columns.size() * (part - 1) / parts;
        int to = columns.size() * part / parts;
        Set<Long> own = new HashSet<>();
        for (int index = from; index < to; index++) {
            own.add(column(columns.get(index)[0], columns.get(index)[1]));
        }
        return own;
    }

    private static long column(int dx, int dz) {
        return ((long) dx << 32) ^ (dz & 0xFFFFFFFFL);
    }
}
