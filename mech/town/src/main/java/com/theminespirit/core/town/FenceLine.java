package com.theminespirit.core.town;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class FenceLine {
    private static final int[] MX = {-1, -1, 0, 1, 1, 1, 0, -1};
    private static final int[] MZ = {0, -1, -1, -1, 0, 1, 1, 1};

    private final List<ColumnMask.Cell> posts;
    private final List<Integer> gates;
    private final Set<ColumnMask.Cell> lookup;

    public FenceLine(List<ColumnMask.Cell> posts, List<Integer> gates) {
        this.posts = List.copyOf(Objects.requireNonNull(posts, "Столбы ограды не могут быть null"));
        this.gates = List.copyOf(Objects.requireNonNull(gates, "Калитки ограды не могут быть null"));
        for (int gate : this.gates) {
            if (gate < 0 || gate >= this.posts.size()) {
                throw new IllegalArgumentException("Калитка " + gate + " вне ограды из " + this.posts.size()
                        + " столбов: калитка стоит в линии ограды");
            }
        }
        this.lookup = Set.copyOf(this.posts);
    }

    public List<ColumnMask.Cell> posts() {
        return posts;
    }

    public List<Integer> gates() {
        return gates;
    }

    public boolean hasPost(int x, int z) {
        return lookup.contains(new ColumnMask.Cell(x, z));
    }

    public boolean straight(int index) {
        ColumnMask.Cell prev = posts.get(Math.floorMod(index - 1, posts.size()));
        ColumnMask.Cell here = posts.get(index);
        ColumnMask.Cell next = posts.get(Math.floorMod(index + 1, posts.size()));
        return (prev.x() == here.x() && here.x() == next.x()) || (prev.z() == here.z() && here.z() == next.z());
    }

    public static List<ColumnMask.Cell> loop(ColumnMask area) {
        Objects.requireNonNull(area, "Область ограды не может быть null: ограда идёт по её краю");
        if (area.isEmpty()) {
            throw new IllegalArgumentException("Область ограды пуста: огораживать нечего");
        }
        ColumnMask.Cell start = area.cells().stream()
                .min((a, b) -> a.z() != b.z() ? Integer.compare(a.z(), b.z()) : Integer.compare(a.x(), b.x()))
                .orElseThrow();
        List<ColumnMask.Cell> trace = new ArrayList<>();
        trace.add(start);
        ColumnMask.Cell current = start;
        int back = 0;
        int first = -1;
        int limit = 8 * area.count() + 8;
        while (trace.size() < limit) {
            int found = -1;
            for (int turn = 1; turn <= 8; turn++) {
                int direction = (back + turn) % 8;
                if (area.contains(current.x() + MX[direction], current.z() + MZ[direction])) {
                    found = direction;
                    break;
                }
            }
            if (found < 0 || (current.equals(start) && found == first)) {
                break;
            }
            if (first < 0) {
                first = found;
            }
            current = new ColumnMask.Cell(current.x() + MX[found], current.z() + MZ[found]);
            trace.add(current);
            back = found % 2 == 0 ? (found + 6) % 8 : (found + 5) % 8;
        }
        return orthogonal(trace, area);
    }

    private static List<ColumnMask.Cell> orthogonal(List<ColumnMask.Cell> trace, ColumnMask area) {
        List<ColumnMask.Cell> out = new ArrayList<>();
        for (int index = 0; index < trace.size(); index++) {
            ColumnMask.Cell here = trace.get(index);
            ColumnMask.Cell next = trace.get((index + 1) % trace.size());
            out.add(here);
            if (here.x() != next.x() && here.z() != next.z()) {
                ColumnMask.Cell viaX = new ColumnMask.Cell(next.x(), here.z());
                ColumnMask.Cell viaZ = new ColumnMask.Cell(here.x(), next.z());
                out.add(area.contains(viaX.x(), viaX.z()) ? viaX : viaZ);
            }
        }
        List<ColumnMask.Cell> clean = new ArrayList<>();
        for (ColumnMask.Cell cell : out) {
            if (clean.isEmpty() || !clean.get(clean.size() - 1).equals(cell)) {
                clean.add(cell);
            }
        }
        if (clean.size() > 1 && clean.get(0).equals(clean.get(clean.size() - 1))) {
            clean.remove(clean.size() - 1);
        }
        return List.copyOf(clean);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FenceLine line && posts.equals(line.posts) && gates.equals(line.gates);
    }

    @Override
    public int hashCode() {
        return Objects.hash(posts, gates);
    }

    @Override
    public String toString() {
        return "FenceLine[столбов " + posts.size() + ", калиток " + gates.size() + "]";
    }
}
