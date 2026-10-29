package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class BreakSkipTally {
    public static final int LOGGED_SAMPLES = 3;

    public enum Reason {
        OUT_OF_REACH("вне досягаемости"),
        NO_SIGHT("не виден"),
        CHUNK_NOT_LOADED("чанк не загружен"),
        NO_STAND("без стоянки"),
        UNBREAKABLE("неразрушим"),
        VANISHED("исчез");

        private final String title;

        Reason(String title) {
            this.title = title;
        }

        public String title() {
            return title;
        }
    }

    public record Sample(
            BlockCoord target, Reason reason, @Nullable BlockCoord stand) {
        public Sample {
            Objects.requireNonNull(target, "Блок пропуска не может быть null: по нему ищут причину отказа");
            Objects.requireNonNull(reason, "Причина пропуска не может быть null: без неё счётчики не различить");
        }
    }

    private final Map<Reason, Integer> counts = new EnumMap<>(Reason.class);
    private final List<Sample> samples = new ArrayList<>();

    public void record(Reason reason) {
        record(reason, 1);
    }

    public void record(Reason reason, int count) {
        Objects.requireNonNull(reason, "Причина пропуска не может быть null: без неё счётчики не различить");
        if (count < 0) {
            throw new IllegalArgumentException("Число пропусков не может быть отрицательным: " + count);
        }
        if (count > 0) {
            counts.merge(reason, count, Integer::sum);
        }
    }

    public Optional<Sample> record(Reason reason, BlockCoord target, @Nullable BlockCoord stand) {
        Objects.requireNonNull(reason, "Причина пропуска не может быть null: без неё счётчики не различить");
        counts.merge(reason, 1, Integer::sum);
        if (samples.size() >= LOGGED_SAMPLES) {
            return Optional.empty();
        }
        Sample sample = new Sample(target, reason, stand);
        samples.add(sample);
        return Optional.of(sample);
    }

    public int count(Reason reason) {
        return counts.getOrDefault(reason, 0);
    }

    public int total() {
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }

    public List<Sample> samples() {
        return List.copyOf(samples);
    }

    public String text() {
        StringBuilder text = new StringBuilder();
        for (Reason reason : Reason.values()) {
            int count = count(reason);
            if (count == 0) {
                continue;
            }
            if (!text.isEmpty()) {
                text.append(", ");
            }
            text.append(reason.title()).append(": ").append(count);
        }
        return text.toString();
    }
}
