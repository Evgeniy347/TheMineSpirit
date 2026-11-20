package com.theminespirit.neoforge.board;

import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.mining.DescentData;
import java.util.Objects;
import java.util.Optional;

public final class DescentSync {
    private static final String SCHEME = "mine_descent_v1.txt";
    private static final String TABLE = "mining/descent.json";

    private DescentSync() {}

    public static Optional<String> mismatch(Schematic schematic, DescentData data) {
        Objects.requireNonNull(schematic, "Схема спуска не может быть null: у неё нет шага повтора и факелов");
        Objects.requireNonNull(data, "Данные спуска не могут быть null: без них нечего сверять");
        Schematic.Repeat repeat = schematic.repeat();
        if (repeat == null) {
            return Optional.of("схема " + SCHEME
                    + " без repeat: спуск не знает, докуда копать и как часто ставить факелы, а " + TABLE
                    + " задаёт targetY=" + data.targetY() + " и torchInterval=" + data.torchInterval());
        }
        if (!(repeat.until() instanceof Schematic.Repeat.TargetY target)) {
            return Optional.of("схема " + SCHEME
                    + " повторяется по числу сегментов, а " + TABLE + " задаёт targetY=" + data.targetY()
                    + ": спуск остановится не на цели таблицы");
        }
        if (target.y() != data.targetY()) {
            return Optional.of("targetY разъехался: " + SCHEME + " копает до y=" + target.y() + ", а " + TABLE
                    + " задаёт y=" + data.targetY());
        }
        if (repeat.torchEvery() != data.torchInterval()) {
            return Optional.of("интервал факелов разъехался: " + SCHEME + " ставит факел каждые "
                    + repeat.torchEvery() + " сегментов, а " + TABLE + " задаёт " + data.torchInterval()
                    + ": ствол будет темнее или дороже схемы");
        }
        return Optional.empty();
    }
}
