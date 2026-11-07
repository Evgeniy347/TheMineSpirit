package com.theminespirit.core.board;

import com.theminespirit.core.town.TownSnapshot;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class BuildProgressSnapshots {
    private final Map<String, TownSnapshot.Progress> last = new LinkedHashMap<>();

    public void remember(String id, TownSnapshot.Progress progress) {
        Objects.requireNonNull(id, "id постройки не может быть null: снимок адресный");
        Objects.requireNonNull(progress, "Снимок прогресса не может быть null: пустой — Progress.NONE");
        if (progress.total() > 0) {
            last.put(id, progress);
        }
    }

    public Optional<TownSnapshot.Progress> last(String id) {
        Objects.requireNonNull(id, "id постройки не может быть null: снимок адресный");
        return Optional.ofNullable(last.get(id));
    }

    public void forget(String id) {
        Objects.requireNonNull(id, "id постройки не может быть null: снимок адресный");
        last.remove(id);
    }
}
