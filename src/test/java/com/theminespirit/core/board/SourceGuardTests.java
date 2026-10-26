package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SourceGuardTests {
    @Test
    void run_OneSourceThrows_OthersRunAndRoundMarkedFailed() {
        List<String> reports = new ArrayList<>();
        List<String> ran = new ArrayList<>();
        SourceGuard guard = new SourceGuard((source, error, repeated) -> reports.add(source + ":" + repeated));

        guard.startRound();
        guard.run("wood", () -> {
            throw new IllegalStateException("repair.txt не найден");
        });
        guard.run("tools", () -> ran.add("tools"));
        Set<String> causes = guard.call("left_ore", () -> Set.of("left_ore:1"), Set.of());

        assertEquals(
                List.of(List.of("wood:false"), List.of("tools"), Set.of("left_ore:1"), true),
                List.of(reports, ran, causes, guard.failedRound()),
                "INC-88: исключение одного источника доски не роняет тик сервера — пишется [incident], остальные "
                        + "источники идут, а раунд помечен сбойным, чтобы не снять живые задания упавшего источника");
    }

    @Test
    void run_SameErrorNextRound_ReportedAsRepeatAndRoundResets() {
        List<Boolean> repeats = new ArrayList<>();
        SourceGuard guard = new SourceGuard((source, error, repeated) -> repeats.add(repeated));
        Runnable broken = () -> {
            throw new IllegalStateException("repair.txt не найден");
        };

        guard.startRound();
        guard.run("wood", broken);
        guard.startRound();
        guard.run("wood", broken);
        guard.startRound();
        guard.run("wood", () -> {});

        assertEquals(
                List.of(List.of(false, true), false),
                List.of(repeats, guard.failedRound()),
                "Тот же сбой в следующей пересборке — короткая строка без стека; источник снова пробуется каждую "
                        + "пересборку, и после починки раунд чистый");
    }
}
