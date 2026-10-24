package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Сторож задания доски, которое стоит (INC-65): дух «занят» заданием, но скрипта нет (задача не стартовала) или он
 * идёт к месту и не сдвигается. Дольше {@link #STALL_TICKS} — задание проваливается с причиной, а не висит «busy»
 * без движения минутами. Долгая работа на месте (плавка, крафт) — не простой: у неё свой скрипт.
 */
public final class TaskStall {
    public static final long STALL_TICKS = 1200;
    // Дух двигается, но прогресс задачи (шаг, скрипт, сделано) не меняется — бродит без дела (INC-67: 10 минут по
    // пещере под участком). Дольше любого честного перехода к месту работы.
    public static final long NO_PROGRESS_TICKS = 3600;
    private static final String GOTO = "goto";

    private final Map<UUID, Seen> seen = new HashMap<>();
    private final Map<UUID, Progress> progress = new HashMap<>();

    private record Progress(String key, long since) {}

    // true — ключ прогресса задачи (шаг, скрипт, сделано) не менялся NO_PROGRESS_TICKS.
    public boolean noProgress(UUID spirit, String key, long now) {
        Objects.requireNonNull(spirit, "Дух не может быть null: прогресс считается на конкретного духа");
        Objects.requireNonNull(key, "Ключ прогресса не может быть null: без него не видно, сдвинулась ли задача");
        Progress last = progress.get(spirit);
        if (last == null || !last.key().equals(key)) {
            progress.put(spirit, new Progress(key, now));
            return false;
        }
        return now - last.since() >= NO_PROGRESS_TICKS;
    }

    private record Seen(@Nullable String script, BlockCoord pos, long since) {}

    // true — задание стоит не меньше STALL_TICKS: без скрипта или в подходе на одном месте.
    public boolean stalled(UUID spirit, @Nullable String script, BlockCoord pos, long now) {
        Objects.requireNonNull(spirit, "Дух не может быть null: простой считается на конкретного духа");
        Objects.requireNonNull(pos, "Позиция не может быть null: простой — это «не сдвинулся»");
        boolean idleKind = script == null || GOTO.equals(script);
        Seen last = seen.get(spirit);
        if (!idleKind) {
            seen.remove(spirit);
            return false;
        }
        if (last == null || !Objects.equals(last.script(), script) || !near(last.pos(), pos)) {
            seen.put(spirit, new Seen(script, pos, now));
            return false;
        }
        return now - last.since() >= STALL_TICKS;
    }

    public void forget(UUID spirit) {
        seen.remove(spirit);
        progress.remove(spirit);
    }

    private static boolean near(BlockCoord first, BlockCoord second) {
        return Math.abs(first.x() - second.x()) <= 1
                && Math.abs(first.y() - second.y()) <= 1
                && Math.abs(first.z() - second.z()) <= 1;
    }
}
