package com.theminespirit.core.board.audit;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;

public record AuditMiss(Cause cause, BlockCoord cell, String cached, String actual, String wanted, Context context) {
    public enum Cause {
        NOT_FILLED,
        EVENT_MISSED,
        EQUIVALENCE,
        LEASE_OPTIMISTIC,
        STALE_TICKET
    }

    public static final String ALREADY_DONE = "ALREADY_DONE";
    public static final String FOREIGN_BLOCK = "FOREIGN_BLOCK";
    public static final String CACHE_STALE = "CACHE_STALE";

    public record Context(
            String groupKey,
            int revision,
            long loadedTick,
            long lastVisit,
            int changes,
            String lastChange,
            String kind) {
        public Context {
            Objects.requireNonNull(groupKey, "Ключ группы не может быть null: без него не перезалить чанк");
            Objects.requireNonNull(lastChange, "Последнее изменение не может быть null: пустое — пустая строка");
            Objects.requireNonNull(kind, "Вид промаха не может быть null: иначе нечем объяснить задачу духа");
        }

        public static Context of(
                String groupKey,
                int revision,
                long loadedTick,
                long lastVisit,
                int changes,
                String lastChange,
                String kind) {
            return new Context(groupKey, revision, loadedTick, lastVisit, changes, lastChange, kind);
        }
    }

    public AuditMiss {
        Objects.requireNonNull(cause, "Причина промаха не может быть null: без неё инцидент не разобрать");
        Objects.requireNonNull(cell, "Клетка промаха не может быть null: её перезаливают из мира");
        Objects.requireNonNull(cached, "Факт кэша не может быть null: неизвестный блок — air");
        Objects.requireNonNull(actual, "Блок мира не может быть null: иначе нечем доказать промах");
        Objects.requireNonNull(wanted, "Ожидание не может быть null: задача без ожидания бессмысленна");
        Objects.requireNonNull(
                context, "Контекст не может быть null: без тика заполнения чанка промах не воспроизвести");
    }

    public static AuditMiss of(
            Cause cause, String kind, BlockCoord cell, String cached, String actual, Wanted wanted, Context context) {
        return new AuditMiss(
                cause,
                cell,
                cached,
                actual,
                wanted.text(),
                new Context(
                        context.groupKey(),
                        context.revision(),
                        context.loadedTick(),
                        context.lastVisit(),
                        context.changes(),
                        context.lastChange(),
                        kind));
    }

    public AuditMiss withGroupKey(String groupKey) {
        Objects.requireNonNull(groupKey, "Ключ группы промаха не может быть null: карантин теряет клетку");
        Context context = this.context;
        return new AuditMiss(
                cause,
                cell,
                cached,
                actual,
                wanted,
                new Context(
                        groupKey,
                        context.revision(),
                        context.loadedTick(),
                        context.lastVisit(),
                        context.changes(),
                        context.lastChange(),
                        context.kind()));
    }

    public record Signs(boolean filled, boolean changeInRing, boolean equivalentByRule, boolean ticketStale) {}

    public static Cause classify(Signs signs) {
        if (!signs.filled()) {
            return Cause.NOT_FILLED;
        }
        if (signs.ticketStale()) {
            return Cause.STALE_TICKET;
        }
        if (signs.equivalentByRule()) {
            return Cause.EQUIVALENCE;
        }
        return signs.changeInRing() ? Cause.EVENT_MISSED : Cause.LEASE_OPTIMISTIC;
    }

    public String text() {
        return "AUDIT-MISS/" + context.kind() + ": клетка " + cell + " ожидала факт " + cached + ", стоит " + actual
                + " (надо " + wanted + "), группа " + context.groupKey() + "@" + context.revision()
                + ", чанк заполнен на тике " + context.loadedTick() + ", последний обход " + context.lastVisit()
                + ", изменений в журнале чанка: " + context.changes()
                + (context.lastChange().isBlank() ? "" : ", последнее: " + context.lastChange())
                + ", промах кэша: " + cause.name() + "."
                + " Причина: кэш утверждает " + cached + ", мир " + actual
                + " — задача выдана на несуществующую работу."
                + " Надо: перезалить чанк, группу в QUARANTINED, причину классифицировать.";
    }
}
