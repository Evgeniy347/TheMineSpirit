package com.theminespirit.neoforge.inventory;

import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import org.jspecify.annotations.Nullable;

final class ContainerLocation {
    sealed interface Result permits Waiting, Ready, Finished, Failed {}

    record Waiting() implements Result {}

    record Ready(Container container, BlockPos pos, ContainerTask.@Nullable AutoMove move) implements Result {}

    record Finished() implements Result {}

    record Failed(String reason) implements Result {}

    private static final Result WAITING = new Waiting();
    private static final Result FINISHED = new Finished();

    private final @Nullable ContainerFinder finder;
    private final @Nullable AutoContainerScan scan;

    ContainerLocation(ContainerScan.Choice choice) {
        this.finder = choice.finder();
        this.scan = choice.scan();
    }

    Result tick(Supplier<ContainerTask.AutoMove> nextAutoMove) {
        if (scan != null) {
            if (!scan.tick()) {
                return WAITING;
            }
            ContainerTask.AutoMove move = nextAutoMove.get();
            return move == null ? FINISHED : ready(move);
        }
        ContainerFinder current = Objects.requireNonNull(finder, "Цель контейнера не выбрана: скан и поиск пусты");
        if (!current.tick()) {
            return WAITING;
        }
        if (current.found() == null) {
            return new Failed(current.refusal());
        }
        BlockPos pos = current.found();
        return switch (ContainerAccess.open(current.level(), pos)) {
            case ContainerAccess.Opened opened -> new Ready(opened.container(), pos.immutable(), null);
            case ContainerAccess.Refused refused -> new Failed(refused.reason());
        };
    }

    boolean auto() {
        return scan != null;
    }

    AutoContainerScan autoScan() {
        return Objects.requireNonNull(scan, "Автоматический скан не создан: запрошен не тот режим");
    }

    void updateSnapshot(int index) {
        autoScan().updateSnapshot(index);
    }

    int maxSlicesPerTick() {
        return scan == null
                ? Objects.requireNonNull(finder, "Поиск контейнера не создан").maxSlicesPerTick()
                : scan.maxSlicesPerTick();
    }

    private static Result ready(ContainerTask.AutoMove move) {
        return new Ready(
                move.container(),
                new BlockPos(move.pos().x(), move.pos().y(), move.pos().z()),
                move);
    }
}
