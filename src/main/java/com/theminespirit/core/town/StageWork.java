package com.theminespirit.core.town;

import java.util.List;
import java.util.Objects;

public record StageWork(StageSite site, List<PlannedBlock> blocks, List<SchemeBuild> builds, String repairs) {
    public StageWork(StageSite site, List<PlannedBlock> blocks) {
        this(site, blocks, List.of(), "");
    }

    public StageWork(StageSite site, List<PlannedBlock> blocks, List<SchemeBuild> builds) {
        this(site, blocks, builds, "");
    }

    public StageWork {
        Objects.requireNonNull(site, "Место этапа не может быть null: задание этапа берёт места из плана");
        blocks = List.copyOf(Objects.requireNonNull(blocks, "Блоки задания не могут быть null: пусто — пустой список"));
        builds = List.copyOf(
                Objects.requireNonNull(builds, "Стройки задания не могут быть null: пусто — пустой список"));
        Objects.requireNonNull(repairs, "Восстанавливаемый шаг не может быть null: без восстановления — пустая строка");
    }

    public StageWork repairing(String step) {
        return new StageWork(site, blocks, builds, step);
    }
}
