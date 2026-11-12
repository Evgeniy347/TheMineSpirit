package com.theminespirit.core.board.lease;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LeaseAbandonTests {
    private static final UUID SPIRIT = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final BlockCoord PILLAR = new BlockCoord(-180, 72, -510);

    @Test
    void abandon_OrphanedLease_FreesCellsForClearing() {
        LeaseBook book = new LeaseBook();
        Lease lease = book.open(SPIRIT, "t1", "scaffold", 10);
        book.record(lease.id(), LeaseCell.placed(PILLAR));
        book.orphan(lease.id());

        book.abandon(lease.id());

        assertEquals(
                Optional.empty(),
                book.owner(PILLAR),
                "брошенная аренда, которую откат не закрыл за undoMaxFailures попыток, снимается: её клетки больше"
                        + " ничьи и их берёт расчистка как обычный мусор (сервер 12.11: «клетка не видна из глаз»,"
                        + " «дух не встал за 40 тиков» — столбы оставались навсегда). Сломается: вечные столбы."
                        + " Проверить при смене: ClearTargets.offLimits и UndoLeaseScript.fail");
        assertEquals(
                LeaseStatus.DONE,
                book.require(lease.id()).status(),
                "снятая аренда закрыта (DONE), а не брошена: иначе откат снова возьмёт её в работу по кругу");
    }

    @Test
    void abandon_ActiveLease_Refused() {
        LeaseBook book = new LeaseBook();
        Lease lease = book.open(SPIRIT, "t1", "scaffold", 10);
        book.record(lease.id(), LeaseCell.placed(PILLAR));

        assertThrows(
                IllegalStateException.class,
                () -> book.abandon(lease.id()),
                "действующую аренду снимать нельзя: под хозяином его же столб, и расчистка выбьет опору."
                        + " Проверить при смене: LeaseData.undoFailed проверяет статус ORPHANED");
    }

    @Test
    void exhausted_FailuresAtLimit_GivesUp() {
        assertTrue(
                LeaseUndoLimit.exhausted(3, 3),
                "после undoMaxFailures неудачных откатов (board.json) аренду снимают: бесконечные попытки держат"
                        + " клетки и тратят духов. Проверить при смене: undoMaxFailures");
        assertFalse(
                LeaseUndoLimit.exhausted(2, 3),
                "до предела откат ещё пробуют: временная помеха (дух рядом, моб) не должна сносить защиту сразу");
    }
}
