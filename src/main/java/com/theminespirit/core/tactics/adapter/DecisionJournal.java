package com.theminespirit.core.tactics.adapter;

import com.theminespirit.core.board.ConfigStamp;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class DecisionJournal {
    public static final int TICKS_PER_SECOND = 20;

    private static final long NEVER = Long.MIN_VALUE / 2;

    private final int capacity;
    private final int minGapTicks;
    private final Deque<DecisionRecord> records = new ArrayDeque<>();
    private Optional<DecisionSnapshot> baseline = Optional.empty();
    private long lastRecordTick = NEVER;

    public DecisionJournal(int capacity, int maxRecordsPerSecond) {
        if (capacity < 1) {
            throw new IllegalArgumentException("DecisionJournal.capacity = " + capacity + ": журнал обязан хранить "
                    + "хотя бы одну запись, иначе REST и команда не покажут ничего");
        }
        if (maxRecordsPerSecond < 1 || maxRecordsPerSecond > TICKS_PER_SECOND) {
            throw new IllegalArgumentException("DecisionJournal.maxRecordsPerSecond = " + maxRecordsPerSecond
                    + ": предел частоты лежит от 1 до " + TICKS_PER_SECOND + " записей в секунду (одна за тик), "
                    + "иначе предел не ограничивает ничего или запрещает журнал совсем");
        }
        this.capacity = capacity;
        this.minGapTicks = TICKS_PER_SECOND / maxRecordsPerSecond;
    }

    public Optional<DecisionRecord> offer(DecisionObservation observation) {
        return offer(observation, ConfigStamp.current());
    }

    public Optional<DecisionRecord> offer(DecisionObservation observation, String configHash) {
        Objects.requireNonNull(observation, "observation не может быть null: журнал пишет только наблюдаемую оценку");
        Objects.requireNonNull(configHash, "configHash не может быть null: запись обязана нести "
                + "версию конфигурации, по которой принято решение");
        DecisionSnapshot next = observation.snapshot();
        Optional<DecisionSnapshot> previous = baseline;
        if (previous.isEmpty()) {
            baseline = Optional.of(next);
            return Optional.empty();
        }
        Optional<String> changed = previous.get().changedField(next);
        if (changed.isEmpty()) {
            return Optional.empty();
        }
        if (observation.tick() - lastRecordTick < minGapTicks) {
            return Optional.empty();
        }
        DecisionRecord record = record(observation, changed.get(), previous.get(), next, configHash);
        records.addLast(record);
        while (records.size() > capacity) {
            records.removeFirst();
        }
        lastRecordTick = observation.tick();
        baseline = Optional.of(next);
        return Optional.of(record);
    }

    public List<DecisionRecord> last(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("DecisionJournal.last.limit = " + limit + ": просят хотя бы одну "
                    + "запись, иначе запрос бессмыслен");
        }
        List<DecisionRecord> newestFirst = new ArrayList<>();
        for (Iterator<DecisionRecord> fromNewest = records.descendingIterator(); fromNewest.hasNext();) {
            if (newestFirst.size() == limit) {
                break;
            }
            newestFirst.add(fromNewest.next());
        }
        return List.copyOf(newestFirst);
    }

    public int capacity() {
        return capacity;
    }

    public int minGapTicks() {
        return minGapTicks;
    }

    private static DecisionRecord record(DecisionObservation observation, String changed, DecisionSnapshot before,
            DecisionSnapshot after, String configHash) {
        return new DecisionRecord(observation.tick(), observation.spiritId(), observation.spiritName(), changed,
                before.value(changed), after.value(changed), observation.outcome().trace().rule(),
                observation.outcome().trace().hold(), DecisionReasons.topFactors(observation.outcome().trace(),
                        observation.outcome().decision()), observation.facts(), configHash);
    }
}
