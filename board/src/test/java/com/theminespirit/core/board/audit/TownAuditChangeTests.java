package com.theminespirit.core.board.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.ConstructionBatches.ChunkCell;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TownAuditChangeTests {

    private static final ChunkCell CHUNK = new ChunkCell(0, 0);
    private static final BlockCoord CELL = new BlockCoord(0, 64, 0);

    private static AuditRegistry registry(AuditBudgets budgets) {
        AuditRegistry registry = new AuditRegistry(budgets, 1, 1);
        registry.plan(1, 64);
        registry.retain(Set.of(CHUNK));
        registry.ensure(CHUNK);
        registry.fill(2048, cell -> "minecraft:stone", 1L);
        return registry;
    }

    private static ChangeRing.Change change(
            BlockCoord cell, String before, String after, ChangeRing.Actor actor, String ticket) {
        return new ChangeRing.Change(10L, cell, before, after, actor, "test", ticket, "");
    }

    @Test
    void auditChanges_ForeignBlock_QueuesWithoutChangingCache() {
        AuditRegistry registry = registry(AuditBudgets.DEFAULT);
        ChangeRing.Change change = change(CELL, "minecraft:stone", "minecraft:air", ChangeRing.Actor.PLAYER, "");

        assertTrue(
                registry.record(change),
                "Чужая рука помечает клетку грязной: иначе аудит продолжит"
                        + " утверждать камень там, где игрок оставил воздух. При смене правила перепроверь record и dirty.");
        assertEquals(
                "minecraft:stone",
                registry.blockAt(CELL),
                "До сверки кэш хранит прежний факт: иначе"
                        + " инцидент потеряет доказательство того, что мир изменился мимо аудита. При смене правила"
                        + " перепроверь ветку own в record.");
        assertEquals(
                Set.of(CELL),
                registry.dirty(),
                "Грязная клетка указана в наборе: иначе фоновая спираль"
                        + " не найдёт место расхождения и группа останется в работе. При смене индекса перепроверь dirty.");
        assertEquals(
                1,
                registry.pending(),
                "Изменение ждёт секундной разборки: иначе чужая правка потеряется"
                        + " до следующего полного обхода. При смене очереди перепроверь pending и drain.");
        assertEquals(
                change,
                registry.ring(CELL).last(),
                "Журнал чанка сохраняет причинную запись: иначе"
                        + " AUDIT-MISS не назовёт актёра и время промаха. При смене формата перепроверь ChangeRing.Change.");
    }

    @Test
    void auditChanges_OwnIssuedTicket_UpdatesCacheWithoutDirty() {
        AuditRegistry registry = registry(AuditBudgets.DEFAULT);
        registry.issued("ticket-1", "group:building:storage_3");
        ChangeRing.Change change = change(
                CELL, "minecraft:stone", "minecraft:oak_stairs[facing=north]", ChangeRing.Actor.SPIRIT, "ticket-1");

        assertFalse(
                registry.record(change),
                "Свой билет не грязнит выданную группу: иначе дух завершит блок,"
                        + " а аудит тут же переоткроет ту же работу по устаревшему факту. При смене правила перепроверь"
                        + " issued и ветку own.");
        assertEquals(
                "minecraft:oak_stairs[facing=north]",
                registry.blockAt(CELL),
                "Своё действие применяется к кэшу сразу: иначе следующий предмет того же билета увидит старый"
                        + " камень и сломает уже поставленную ступеньку. При смене правила перепроверь cache.put.");
        assertTrue(
                registry.dirty().isEmpty(),
                "После своего действия клетка чиста: иначе каскад событие→группа"
                        + "→билет замкнётся в петлю и остановит стройку. При смене правила перепроверь dirty.remove.");
        assertEquals(
                0,
                registry.pending(),
                "Своё изменение не ждёт чужой сверки: иначе секундная очередь"
                        + " будет считать штатный прогресс духов промахом кэша. При смене очереди перепроверь pending.remove.");
        assertEquals(
                "group:building:storage_3",
                registry.ring(CELL).last().group(),
                "Билет получает группу"
                        + " в журнале: иначе инцидент не свяжет блок с выданным заданием. При смене формата перепроверь"
                        + " нормализацию group в record.");
    }

    @Test
    void auditChanges_DuplicateCellTick_KeepsFirstCause() {
        AuditRegistry registry = registry(AuditBudgets.DEFAULT);
        ChangeRing.Change first = change(CELL, "minecraft:stone", "minecraft:air", ChangeRing.Actor.PLAYER, "");
        ChangeRing.Change duplicate = change(CELL, "minecraft:stone", "minecraft:air", ChangeRing.Actor.NATURAL, "");

        assertTrue(
                registry.record(first),
                "Первое изменение принимается: иначе настоящий взрыв или поломка"
                        + " игрока останутся вне аудита. При смене правила перепроверь record.");
        assertFalse(
                registry.record(duplicate),
                "Двойной сигнал той же клетки на том же тике гасится: иначе"
                        + " neighbor-notify подменит игрока природой, и инцидент укажет неверного виновника. При смене"
                        + " правила перепроверь проверку previous.tick.");
        assertEquals(
                1,
                registry.ring(CELL).size(),
                "В журнале одна причинная запись: иначе кольцо 64 быстро"
                        + " вытеснит настоящие изменения дубликатами. При смене правила перепроверь ChangeRing.add.");
        assertEquals(
                1,
                registry.pending(),
                "Очередь хранит одну клетку, а не два сигнала: иначе бюджет"
                        + " секундной разборки уйдёт на повтор. При смене очереди перепроверь pending.put.");
    }

    @Test
    void auditChanges_DrainBudget_LeavesDirtyUntilVerified() {
        AuditRegistry registry = registry(new AuditBudgets(2048, 4096, 1, 64));
        BlockCoord second = new BlockCoord(1, 64, 0);
        registry.record(change(CELL, "minecraft:stone", "minecraft:air", ChangeRing.Actor.PLAYER, ""));
        registry.record(change(second, "minecraft:stone", "minecraft:air", ChangeRing.Actor.EXPLOSION, ""));

        List<ChangeRing.Change> drained = registry.drain();
        assertEquals(
                1,
                drained.size(),
                "Бюджет разбора берёт не больше заданного: иначе всплеск взрыва"
                        + " съест такт и задержит духов. При смене лимита перепроверь drainCellsPerTick в audit.json.");
        assertEquals(
                1,
                registry.pending(),
                "Остаток очереди переживает тик: иначе изменения потеряются,"
                        + " а аудит никогда не узнает о дыре. При смене очереди перепроверь drain и pending.");
        assertEquals(
                2,
                registry.dirty().size(),
                "Разбор очереди не объявляет клетки чистыми: иначе фоновая"
                        + " сверка не сравнит кэш с миром и промах останется незамеченным. При смене правила перепроверь clean.");
        registry.clean(CELL);
        assertEquals(
                Set.of(second),
                registry.dirty(),
                "Проверенная клетка снимается с грязи адресно: иначе"
                        + " соседняя дыра останется невыясненной. При смене индекса перепроверь clean.");
    }

    @Test
    void auditChanges_OutsideCache_DoesNotGrowJournal() {
        AuditRegistry registry = registry(AuditBudgets.DEFAULT);
        BlockCoord outside = new BlockCoord(0, 70, 0);

        assertFalse(
                registry.record(change(outside, "minecraft:stone", "minecraft:air", ChangeRing.Actor.PLAYER, "")),
                "Клетка вне окна не инвалидируется: иначе исследованный мир разрастит журналы и очередь"
                        + " аудита далеко за пределами поселения. При смене границы перепроверь cache и retain.");
        assertNull(
                registry.ring(outside),
                "Для чанка без кэша журнал не создаётся: иначе RAM утечёт даже"
                        + " после выгрузки области. При смене правила перепроверь rings.computeIfAbsent.");
        assertEquals(
                0,
                registry.pending(),
                "Внешняя клетка не попадает в секундную очередь: иначе"
                        + " бюджет уйдёт на блоки, которые аудит не обязан сверять. При смене очереди перепроверь record.");
    }

    @Test
    void auditChanges_ReleasedTicket_BecomesForeignAgain() {
        AuditRegistry registry = registry(AuditBudgets.DEFAULT);
        registry.issued("ticket-1", "group:street:s-7@0,0");
        registry.released("ticket-1");

        assertTrue(
                registry.record(change(CELL, "minecraft:stone", "minecraft:air", ChangeRing.Actor.SPIRIT, "ticket-1")),
                "После освобождения билета изменение снова чужое: иначе отменённое задание продолжало бы"
                        + " молча править мир и скрывать дыру от аудита. При смене правила перепроверь released.");
        assertEquals(
                Set.of(CELL),
                registry.dirty(),
                "Отменённый билет оставляет грязную клетку: иначе"
                        + " следующий дух получит устаревший снимок и не найдёт работу. При смене правила перепроверь own.");
    }

    @Test
    void auditChanges_OwnAfterForeignSameTick_ClearsPending() {
        AuditRegistry registry = registry(AuditBudgets.DEFAULT);
        registry.issued("ticket-1", "group:building:storage_3");
        registry.record(change(CELL, "minecraft:stone", "minecraft:air", ChangeRing.Actor.PLAYER, ""));

        assertFalse(
                registry.record(change(
                        CELL,
                        "minecraft:stone",
                        "minecraft:oak_stairs[facing=north]",
                        ChangeRing.Actor.SPIRIT,
                        "ticket-1")),
                "Свой билет важнее чужего сигнала того же тика: иначе поставленный духом блок останется грязным,"
                        + " и аудит снова откроет уже выполненную работу. При смене правила перепроверь ветку own.");
        assertEquals(
                0,
                registry.pending(),
                "Чужая запись того же тика снимается своим действием: иначе"
                        + " секундная сверка увидит устаревший воздух и объявит промах. При смене очереди перепроверь"
                        + " pending.remove в own.");
        assertTrue(
                registry.dirty().isEmpty(),
                "Клетка после своего билета снова чиста: иначе каскад"
                        + " зациклит группу и дух не получит следующий предмет. При смене правила перепроверь dirty.remove.");
    }

    @Test
    void auditChanges_ChunkUnload_DropsJournalAndDirty() {
        AuditRegistry registry = registry(AuditBudgets.DEFAULT);
        registry.record(change(CELL, "minecraft:stone", "minecraft:air", ChangeRing.Actor.PLAYER, ""));

        registry.unloaded(CHUNK);

        assertNull(
                registry.ring(CELL),
                "Выгрузка чанка освобождает журнал: иначе RAM держал бы историю"
                        + " области, которой уже нет в памяти. При смене правила перепроверь unloaded и rings.remove.");
        assertEquals(
                0,
                registry.pending(),
                "Очередь выгруженного чанка очищается: иначе секундный разбор"
                        + " тратил бюджет на блоки недоступного мира. При смене правила перепроверь unloaded.");
        assertTrue(
                registry.dirty().isEmpty(),
                "Грязь выгруженного чанка не переживает выгрузку: иначе"
                        + " фоновая сверка пыталась читать отсутствующий чанк и застревала. При смене правила"
                        + " перепроверь dirty.removeIf.");
    }

    @Test
    void auditChanges_PlanRevision_ReleasesIssuedTickets() {
        AuditRegistry registry = registry(AuditBudgets.DEFAULT);
        registry.issued("ticket-1", "group:building:storage_3");

        registry.plan(2, 64);

        assertTrue(
                registry.record(change(CELL, "minecraft:stone", "minecraft:air", ChangeRing.Actor.SPIRIT, "ticket-1")),
                "Смена плана освобождает старые билеты: иначе дух продолжит менять мир по устаревшей"
                        + " группе и скроет расхождение от аудита. При смене правила перепроверь plan и releaseAll.");
        assertEquals(
                Set.of(CELL),
                registry.dirty(),
                "После смены плана изменение снова грязное: иначе"
                        + " новая ревизия ожиданий не увидит чужую работу. При смене правила перепроверь own.");
    }
}
