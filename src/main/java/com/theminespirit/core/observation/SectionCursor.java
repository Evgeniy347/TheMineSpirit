package com.theminespirit.core.observation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class SectionCursor {
    public static final int SECTION_SIZE = 16;

    private final List<SectionSlice> slices;
    private int position;

    private SectionCursor(List<SectionSlice> slices) {
        this.slices = slices;
    }

    public static SectionCursor of(BlockBox box, BlockCoord center) {
        Objects.requireNonNull(box, "box не может быть null: курсору нужен куб, который резать на срезы");
        Objects.requireNonNull(center, "center не может быть null: по нему срезы сортируются от ближних к дальним");
        List<SectionSlice> slices = new ArrayList<>();
        for (int sx = sectionOf(box.min().x()); sx <= sectionOf(box.max().x()); sx++) {
            for (int sy = sectionOf(box.min().y()); sy <= sectionOf(box.max().y()); sy++) {
                for (int sz = sectionOf(box.min().z()); sz <= sectionOf(box.max().z()); sz++) {
                    slices.add(new SectionSlice(sx, sy, sz, sliceBounds(box, sx, sy, sz)));
                }
            }
        }
        slices.sort(Comparator
                .comparingLong((SectionSlice slice) -> slice.distanceSquared(center))
                .thenComparingInt(SectionSlice::sectionX)
                .thenComparingInt(SectionSlice::sectionY)
                .thenComparingInt(SectionSlice::sectionZ));
        return new SectionCursor(List.copyOf(slices));
    }

    public List<SectionSlice> next(int maxSlices) {
        if (maxSlices < 1) {
            throw new IllegalArgumentException("Размер порции " + maxSlices
                    + " меньше единицы: порция без срезов не двигает курсор, и скан никогда не закончится");
        }
        int end = Math.min(slices.size(), position + maxSlices);
        List<SectionSlice> portion = slices.subList(position, end);
        position = end;
        return portion;
    }

    public boolean isDone() {
        return position >= slices.size();
    }

    public int total() {
        return slices.size();
    }

    public int remaining() {
        return slices.size() - position;
    }

    private static int sectionOf(int coordinate) {
        return Math.floorDiv(coordinate, SECTION_SIZE);
    }

    private static BlockBox sliceBounds(BlockBox box, int sx, int sy, int sz) {
        return new BlockBox(
                new BlockCoord(Math.max(box.min().x(), sx * SECTION_SIZE), Math.max(box.min().y(), sy * SECTION_SIZE),
                        Math.max(box.min().z(), sz * SECTION_SIZE)),
                new BlockCoord(Math.min(box.max().x(), sx * SECTION_SIZE + SECTION_SIZE - 1),
                        Math.min(box.max().y(), sy * SECTION_SIZE + SECTION_SIZE - 1),
                        Math.min(box.max().z(), sz * SECTION_SIZE + SECTION_SIZE - 1)));
    }
}
