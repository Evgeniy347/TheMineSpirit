package com.theminespirit.gametest;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.world.entity.Mob;

final class StepHelper extends GameTestHelper {
    private final long startTick;
    private final List<Runnable> eachTick = new ArrayList<>();
    private final List<Timed> timed = new ArrayList<>();
    private final List<Runnable> stepEnd = new ArrayList<>();
    private Runnable criterion;
    private boolean succeeded;
    private String lastFailure = "итоговая проверка ещё не запускалась";

    StepHelper(GameTestHelper scene) {
        super(scene.testInfo);
        this.startTick = scene.getTick();
    }

    void atStepEnd(Runnable cleanup) {
        stepEnd.add(cleanup);
    }

    @Override
    public void succeed() {
        succeeded = true;
    }

    @Override
    public void succeedWhen(Runnable condition) {
        if (criterion != null) {
            throw new IllegalStateException("у шага уже есть итоговая проверка succeedWhen");
        }
        criterion = condition;
    }

    @Override
    public void succeedIf(Runnable condition) {
        throw unsupported("succeedIf");
    }

    @Override
    public void succeedOnTickWhen(int tick, Runnable condition) {
        throw unsupported("succeedOnTickWhen");
    }

    @Override
    public void failIf(Runnable condition) {
        throw unsupported("failIf");
    }

    @Override
    public void failIfEver(Runnable condition) {
        throw unsupported("failIfEver");
    }

    @Override
    public GameTestSequence startSequence() {
        throw unsupported("startSequence");
    }

    @Override
    public GameTestSequence walkTo(Mob mob, BlockPos pos, float speed) {
        throw unsupported("walkTo");
    }

    @Override
    public void runAtTickTime(long tickTime, Runnable task) {
        timed.add(new Timed(tickTime, task));
    }

    @Override
    public void runAfterDelay(long delay, Runnable task) {
        runAtTickTime(getTick() + delay, task);
    }

    @Override
    public void onEachTick(Runnable task) {
        eachTick.add(task);
    }

    @Override
    public long getTick() {
        return super.getTick() - startTick;
    }

    boolean tick() {
        long now = getTick();
        for (Runnable task : List.copyOf(eachTick)) {
            task.run();
            if (succeeded) {
                return true;
            }
        }
        List<Timed> due = timed.stream().filter(t -> t.tick() <= now).toList();
        timed.removeAll(due);
        for (Timed task : due) {
            task.task().run();
            if (succeeded) {
                return true;
            }
        }
        if (succeeded) {
            return true;
        }
        if (criterion == null) {
            return false;
        }
        try {
            criterion.run();
        } catch (GameTestAssertException notYet) {
            lastFailure = notYet.getMessage();
            return false;
        }
        return true;
    }

    String lastFailure() {
        return lastFailure;
    }

    void end() {
        eachTick.clear();
        timed.clear();
        for (Runnable cleanup : stepEnd) {
            cleanup.run();
        }
        stepEnd.clear();
    }

    private static UnsupportedOperationException unsupported(String method) {
        return new UnsupportedOperationException(
                "шаг укрупнённой сцены не поддерживает " + method + ": перепиши тело через succeedWhen/onEachTick");
    }

    private record Timed(long tick, Runnable task) {}
}
