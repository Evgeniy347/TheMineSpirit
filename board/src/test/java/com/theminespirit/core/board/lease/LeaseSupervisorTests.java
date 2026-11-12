package com.theminespirit.core.board.lease;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LeaseSupervisorTests {
    private static final UUID SPIRIT = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID OTHER = UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa");

    @Test
    void suspendedWithoutResume_AfterTimeout_Orphans() {
        LeaseBook book = new LeaseBook();
        Lease lease = open(book, SPIRIT, "t1");
        Map<String, Long> since = new HashMap<>();

        assertEquals(
                List.of(),
                LeaseSupervisor.due(book, suspended(SPIRIT, "t1"), since, 100, 600),
                "Дух только приостановлен: время паузы ещё не вышло, аренду не бросают. Сломается: чужой откат снимет"
                        + " подмости работающего духа. Проверить при смене: leaseSuspendTimeoutTicks в board.json");
        assertEquals(
                List.of(),
                LeaseSupervisor.due(book, suspended(SPIRIT, "t1"), since, 699, 600),
                "Пауза короче предела: механика ещё может возобновиться. Сломается: граница на тике раньше срока"
                        + " бросит аренду до конца паузы. Проверить при смене: сравнение now - first >= timeout");
        assertEquals(
                List.of(lease),
                LeaseSupervisor.due(book, suspended(SPIRIT, "t1"), since, 700, 600),
                "Пауза без resume дольше предела бросает аренду: дух уже не работает над правкой. Сломается: брошенные"
                        + " столбы останутся в мире навсегда. Проверить при смене: UndoLeaseScript подхватывает брошенные");
    }

    @Test
    void resumeBeforeTimeout_RunningAgain_ClearsSuspendedClock() {
        LeaseBook book = new LeaseBook();
        Lease lease = open(book, SPIRIT, "t1");
        Map<String, Long> since = new HashMap<>();
        LeaseSupervisor.due(book, suspended(SPIRIT, "t1"), since, 100, 600);

        assertEquals(
                List.of(),
                LeaseSupervisor.due(book, running(SPIRIT, "t1"), since, 700, 600),
                "Возобновлённая работа снимает паузу: аренда остаётся действующей. Сломается: долгие работы с паузами"
                        + " теряют подмости посреди дела. Проверить при смене: удаление из suspendedSince");
        assertEquals(
                List.of(),
                LeaseSupervisor.due(book, suspended(SPIRIT, "t1"), since, 1300, 600),
                "Новая пауза считается с нового момента: прежние 600 тиков не украли у неё срок. Сломается: аренду"
                        + " бросят сразу после второй паузы. Проверить при смене: putIfAbsent момента паузы");
        assertEquals(
                List.of(lease),
                LeaseSupervisor.due(book, suspended(SPIRIT, "t1"), since, 1900, 600),
                "Если после возобновления дух снова встал и не вернулся, аренда бросается по новому сроку. Сломается:"
                        + " пауза-возобновление продлевает аренду бесконечно. Проверить при смене: сброс часов паузы");
    }

    @Test
    void ticketMovedToAnotherSpirit_NewOwner_OrphansImmediately() {
        LeaseBook book = new LeaseBook();
        Lease lease = open(book, SPIRIT, "t1");
        Map<UUID, LeaseSupervisor.SpiritState> states = new HashMap<>();
        states.put(SPIRIT, new LeaseSupervisor.SpiritState(false, Set.of()));
        states.put(OTHER, new LeaseSupervisor.SpiritState(false, Set.of("t1")));

        assertEquals(
                List.of(lease),
                LeaseSupervisor.due(book, states, new HashMap<>(), 100, 600),
                "Тикет ушёл другому: прежняя правка стала чужой и бросается, даже если дух ещё жив (но не если он стоит"
                        + " на ней — см. ownerOnOwnCell). Сломается: правки без хозяина копятся. Проверить: ticketOwners");
    }

    @Test
    void missingTicket_NoOwner_OrphansImmediately() {
        LeaseBook book = new LeaseBook();
        Lease lease = open(book, SPIRIT, "t1");

        assertEquals(
                List.of(lease),
                LeaseSupervisor.due(book, running(SPIRIT, "t2"), new HashMap<>(), 100, 600),
                "Тикет потерян или не выдан: механика больше не владеет работой, её правку бросают. Сломается: подмости"
                        + " отменённых заданий стоят вечно. Проверить при смене: доска снимает тикеты (cancelOrphans)");
    }

    @Test
    void runningWithOwnTicket_SameSpirit_StaysActive() {
        LeaseBook book = new LeaseBook();
        open(book, SPIRIT, "t1");

        assertEquals(
                List.of(),
                LeaseSupervisor.due(book, running(SPIRIT, "t1"), new HashMap<>(), 5000, 600),
                "Дух работает над своим тикетом: действующую аренду не трогают. Сломается: откат выбьет опору у"
                        + " работающего духа, он упадёт. Проверить при смене: условие lost в LeaseSupervisor.due");
    }

    @Test
    void deadOrAbsentSpirit_MissingFromAlive_OrphansImmediately() {
        LeaseBook book = new LeaseBook();
        Lease lease = open(book, SPIRIT, "t1");

        assertEquals(
                List.of(lease),
                LeaseSupervisor.due(book, Map.of(), new HashMap<>(), 100, 600),
                "Духа нет среди живых: аренда брошена и ждёт уборки. Сломается: после смерти духа его столбы остаются"
                        + " в мире. Проверить при смене: LeaseLifecycle.onDeath и spiritStates (только живые)");
    }

    @Test
    void legacyWithoutOwner_AlreadyOrphaned_StaysOutOfActive() {
        LeaseBook book = new LeaseBook();
        book.legacy(List.of(new BlockCoord(1, 64, 1)), 10);

        assertTrue(
                book.active().isEmpty(),
                "Legacy-аренда уже брошена: у неё нет владельца, проверять нечего. Сломается: старые метки попадут в"
                        + " активные и никогда не откатятся. Проверить при смене: LeaseBook.legacy");
    }

    @Test
    void ownerOnOwnCell_SuspendedOrTicketMoved_StaysActive() {
        LeaseBook book = new LeaseBook();
        open(book, SPIRIT, "t1");
        BlockCoord pillarTop = new BlockCoord(1, 64, 1);
        Map<String, Long> since = new HashMap<>();
        Map<UUID, LeaseSupervisor.SpiritState> onPillar =
                Map.of(SPIRIT, new LeaseSupervisor.SpiritState(true, Set.of(), pillarTop));
        LeaseSupervisor.due(book, onPillar, since, 100, 600);

        assertEquals(
                List.of(),
                LeaseSupervisor.due(book, onPillar, since, 5000, 600),
                "хозяин стоит на клетке своей аренды — она жива и таймер паузы обновляется, даже если дух на паузе"
                        + " или тикет ушёл (владелец 12.11). Сломается: аренду бросят, чужой откат выбьет столб из-под"
                        + " духа. Проверить при смене: LeaseLifecycle.spiritStates (getOnPos) и OtherSpiritSupport");
        assertTrue(
                since.isEmpty(),
                "пока хозяин на своём столбе, часы паузы сброшены: сойдя, он получает полный срок, а не остаток."
                        + " Сломается: аренду бросят сразу после того, как дух сойдёт. Проверить: suspendedSince");
    }

    private static Lease open(LeaseBook book, UUID spirit, String ticket) {
        Lease lease = book.open(spirit, ticket, "scaffold", 10);
        book.record(lease.id(), LeaseCell.placed(new BlockCoord(1, 64, 1)));
        return book.require(lease.id());
    }

    private static Map<UUID, LeaseSupervisor.SpiritState> suspended(UUID spirit, String ticket) {
        return Map.of(spirit, new LeaseSupervisor.SpiritState(true, Set.of(ticket)));
    }

    private static Map<UUID, LeaseSupervisor.SpiritState> running(UUID spirit, String ticket) {
        return Map.of(spirit, new LeaseSupervisor.SpiritState(false, Set.of(ticket)));
    }
}
