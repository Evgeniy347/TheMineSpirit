package com.theminespirit.core.observation;

import java.util.List;
import java.util.Objects;

public final class SectionSweep {
    public record Batch(List<SectionSlice> slices, boolean cycleDone) {
        public Batch {
            slices = List.copyOf(Objects.requireNonNull(slices, "Порция срезов не может быть null"));
        }
    }

    private final BlockBox box;
    private final BlockCoord center;
    private final int slicesPerStep;
    private SectionCursor cursor;

    public SectionSweep(BlockBox box, BlockCoord center, int slicesPerStep) {
        this.box = Objects.requireNonNull(box, "Куб обхода не может быть null: без него нет срезов");
        this.center = Objects.requireNonNull(center, "Центр обхода не может быть null: от него порядок срезов");
        if (slicesPerStep < 1) {
            throw new IllegalArgumentException(
                    "Порция " + slicesPerStep + " срезов: за шаг нужен хотя бы один срез, иначе обход не движется");
        }
        this.slicesPerStep = slicesPerStep;
        this.cursor = SectionCursor.of(box, center);
    }

    public Batch step() {
        List<SectionSlice> portion = cursor.next(slicesPerStep);
        boolean done = cursor.isDone();
        if (done) {
            cursor = SectionCursor.of(box, center);
        }
        return new Batch(portion, done);
    }
}
