package com.theminespirit.core.board.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.ConstructionBatches.ChunkCell;
import com.theminespirit.core.observation.BlockCoord;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TownAuditVerifyTests {

    private static final ChunkCell CHUNK = new ChunkCell(0, 0);
    private static final BlockCoord CELL = new BlockCoord(0, 64, 0);

    private static Expectation expectation(BlockCoord cell, Wanted wanted, SourceRef source) {
        return new Expectation(cell, wanted, source, Access.SPIRIT);
    }

    private static AuditVerifier verifier(Expectation... expectations) {
        Map<BlockCoord, Expectation> cells = new LinkedHashMap<>();
        for (Expectation expectation : expectations) {
            cells.put(expectation.cell(), expectation);
        }
        AuditVerifier verifier = new AuditVerifier();
        verifier.reset(new ExpectIndex.Index(cells, 0, 0), 0, 0);
        return verifier;
    }

    private static AuditRegistry registry(String initial) {
        AuditRegistry registry = new AuditRegistry(AuditBudgets.DEFAULT, 1, 1);
        registry.plan(1, 64);
        registry.retain(Set.of(CHUNK));
        registry.ensure(CHUNK);
        registry.fill(2048, cell -> initial, 1L);
        return registry;
    }

    private static final class World implements AuditFill.Reader {
        private final Map<BlockCoord, String> cells = new LinkedHashMap<>();
        private int reads;

        World put(BlockCoord cell, String id) {
            cells.put(cell, id);
            return this;
        }

        @Override
        public String blockAt(BlockCoord cell) {
            reads++;
            return cells.get(cell);
        }
    }

    @Test
    void auditVerify_ChangeInRing_ClassifiesEventMissed() {
        AuditRegistry registry = registry("minecraft:stone");
        registry.record(new ChangeRing.Change(
                5L, CELL, "minecraft:stone", "minecraft:air", ChangeRing.Actor.PLAYER, "игрок", "", ""));
        AuditVerifier verifier =
                verifier(expectation(CELL, Wanted.block("minecraft:stone"), SourceRef.building("storage_3")));
        World world = new World().put(CELL, "minecraft:air");

        AuditVerifier.Result result = verifier.verify(registry, world, 10, 10L, 1L);

        assertEquals(
                1,
                result.misses().size(),
                "Расхождение кэша и мира даёт один инцидент: иначе группа"
                        + " будет чинить воздух по устаревшему факту и потеряет блок. При смене правила перепроверь verify.");
        AuditMiss miss = result.misses().get(0);
        assertEquals(
                AuditMiss.Cause.EVENT_MISSED,
                miss.cause(),
                "Запись в кольце объясняет промах: иначе"
                        + " разбор будет искать причину в аренде вместо пропущенного события. При смене классификации"
                        + " перепроверь AuditMiss.classify.");
        assertEquals(
                "group:building:storage_3",
                miss.context().groupKey(),
                "Инцидент называет группу"
                        + " постройки: иначе перезаливка чанка не свяжется с заданием. При смене ключа перепроверь groupKey.");
        assertEquals(
                1,
                miss.context().changes(),
                "Журнал показывает число изменений чанка: иначе причинную"
                        + " цепочку не восстановить. При смене контекста перепроверь ChangeRing.size.");
        assertEquals(
                Set.of(CHUNK),
                result.refill(),
                "Промах возвращает чанк в перезаливку: иначе кэш навсегда"
                        + " останется устаревшим и инцидент повторится каждый цикл. При смене правила перепроверь reload.");
        result.refill().forEach(registry::reload);
        assertNull(
                registry.blockAt(CELL),
                "Перезаливка очищает только свой чанк: иначе следующий цикл"
                        + " снова увидит старый камень и повторит инцидент. При смене правила перепроверь AuditRegistry.reload.");
        registry.fill(2048, cell -> "minecraft:air", 11L);
        assertEquals(
                "minecraft:air",
                registry.blockAt(CELL),
                "После перезаливки кэш совпадает с миром: иначе"
                        + " группа так и останется в карантине без шанса вернуться. При смене правила перепроверь fill и reload.");
    }

    @Test
    void auditVerify_UnknownCache_ClassifiesNotFilled() {
        AuditRegistry registry = new AuditRegistry(AuditBudgets.DEFAULT, 1, 1);
        registry.plan(1, 64);
        registry.retain(Set.of(CHUNK));
        registry.ensure(CHUNK);
        registry.fill(2048, cell -> cell.equals(CELL) ? null : "minecraft:air", 1L);
        AuditVerifier verifier =
                verifier(expectation(CELL, Wanted.block("minecraft:stone"), SourceRef.building("storage_3")));
        World world = new World().put(CELL, "minecraft:stone");

        AuditVerifier.Result result = verifier.verify(registry, world, 10, 10L, 1L);

        assertEquals(
                AuditMiss.Cause.NOT_FILLED,
                result.misses().get(0).cause(),
                "Пустая клетка после полного прохода — промах наполнения: иначе аудит примет unknown за воздух"
                        + " и выдаст ложную поломку. При смене классификации перепроверь Signs.filled.");
        assertEquals(
                "unknown",
                result.misses().get(0).cached(),
                "Неизвестный факт назван явно: иначе текст"
                        + " инцидента будет утверждать воздух, которого в кэше не было. При смене формата перепроверь verify.");
    }

    @Test
    void auditVerify_CacheStillFilling_WaitsWithoutIncident() {
        AuditRegistry registry = new AuditRegistry(AuditBudgets.DEFAULT, 1, 1);
        registry.plan(1, 64);
        registry.retain(Set.of(CHUNK));
        registry.ensure(CHUNK);
        AuditVerifier verifier =
                verifier(expectation(CELL, Wanted.block("minecraft:stone"), SourceRef.building("storage_3")));
        World world = new World().put(CELL, "minecraft:stone");

        AuditVerifier.Result result = verifier.verify(registry, world, 10, 10L, 1L);

        assertEquals(
                1,
                result.unknown(),
                "Незавершённое наполнение ждёт своего тика: иначе старт сервера"
                        + " выдаст тысячи NOT_FILLED раньше, чем чанк вообще прочитан. При смене правила перепроверь filledTick.");
        assertTrue(
                result.misses().isEmpty(),
                "До полного обхода чанка инцидента нет: иначе чат и events.db"
                        + " забиваются временной unknown, которую сама же очередь наполнения и должна убрать. При смене"
                        + " правила перепроверь ветку cache.filledTick.");
    }

    @Test
    void auditVerify_EquivalentActual_ClassifiesEquivalence() {
        AuditRegistry registry = registry("minecraft:stone");
        AuditVerifier verifier = verifier(expectation(CELL, Wanted.ground(64), SourceRef.GROUND));
        World world = new World().put(CELL, "minecraft:grass_block");

        AuditVerifier.Result result = verifier.verify(registry, world, 10, 10L, 1L);

        assertEquals(
                AuditMiss.Cause.EQUIVALENCE,
                result.misses().get(0).cause(),
                "Эквивалентный мир объясняет"
                        + " расхождение: иначе тропа на земле будет числиться поломкой кэша. При смене правила перепроверь"
                        + " BlockEquivalence.check и AuditMiss.classify.");
        assertEquals(
                1,
                result.checked(),
                "Клетка проверена один раз: иначе бюджет ушёл бы повторно на том же месте, а остальной"
                        + " посёлок не получил бы сверку. При смене обхода перепроверь cursor и order.");
    }

    @Test
    void auditVerify_NoRingAndNotEquivalent_ClassifiesLeaseOptimistic() {
        AuditRegistry registry = registry("minecraft:stone");
        AuditVerifier verifier =
                verifier(expectation(CELL, Wanted.block("minecraft:stone"), SourceRef.building("storage_3")));
        World world = new World().put(CELL, "minecraft:air");

        AuditVerifier.Result result = verifier.verify(registry, world, 10, 10L, 1L);

        assertEquals(
                AuditMiss.Cause.LEASE_OPTIMISTIC,
                result.misses().get(0).cause(),
                "Без события и без"
                        + " эквивалентности остаётся оптимистичная аренда: иначе разбор не сможет отличить её от"
                        + " пропущенного события. При смене классификации перепроверь AuditMiss.classify.");
        assertEquals(
                10L,
                registry.cache(CELL).lastVisit(),
                "Сверка отмечает последний обход: иначе инцидент"
                        + " не покажет, когда клетку последний раз видели. При смене контекста перепроверь ChunkCache.visited.");
    }

    @Test
    void auditVerify_BudgetSmall_AdvancesSpiralWithoutLoop() {
        AuditRegistry registry = registry("minecraft:stone");
        BlockCoord second = new BlockCoord(1, 64, 0);
        AuditVerifier verifier = verifier(
                expectation(CELL, Wanted.block("minecraft:stone"), SourceRef.building("storage_3")),
                expectation(second, Wanted.block("minecraft:stone"), SourceRef.building("storage_3")));
        World world = new World().put(CELL, "minecraft:stone").put(second, "minecraft:stone");

        AuditVerifier.Result first = verifier.verify(registry, world, 1, 10L, 1L);
        AuditVerifier.Result secondResult = verifier.verify(registry, world, 1, 11L, 1L);

        assertEquals(
                1,
                first.checked(),
                "Бюджет ограничивает одну клетку: иначе спираль съест такт"
                        + " духов и задержит стройку. При смене лимита перепроверь verifyCellsPerTick.");
        assertEquals(
                1,
                secondResult.checked(),
                "Следующий тик берёт следующую клетку: иначе курсор зациклится"
                        + " на первой и остальной посёлок не проверится никогда. При смене обхода перепроверь cursor.");
        assertEquals(
                2,
                world.reads,
                "Мир прочитан ровно дважды за два тика: иначе повторное чтение первой"
                        + " клетки съест бюджет без пользы. При смене обхода перепроверь reset и order.");
        assertTrue(
                first.misses().isEmpty(),
                "Совпавший факт не создаёт инцидент: иначе журнал забивается"
                        + " ложными промахами. При смене правила перепроверь cached.equals(actual).");
    }

    @Test
    void auditVerify_UnloadedWorld_MarksUnknownWithoutMiss() {
        AuditRegistry registry = registry("minecraft:stone");
        AuditVerifier verifier =
                verifier(expectation(CELL, Wanted.block("minecraft:stone"), SourceRef.building("storage_3")));

        AuditVerifier.Result result = verifier.verify(registry, new World(), 10, 10L, 1L);

        assertEquals(
                1,
                result.unknown(),
                "Незагруженный мир считается unknown: иначе аудит объявит"
                        + " воздух настоящим блоком и выдаст ложную работу. При смене правила перепроверь actual == null.");
        assertTrue(
                result.misses().isEmpty(),
                "Отсутствие мира не является промахом кэша: иначе выгрузка"
                        + " чанка заполнит чат ложными инцидентами. При смене правила перепроверь ветку unknown.");
        assertNull(
                registry.blockAt(CELL),
                "Клетка становится unknown в кэше: иначе группа продолжит"
                        + " считать выгруженный чанк заполненным. При смене правила перепроверь markUnknown.");
        assertFalse(
                result.refill().contains(CHUNK),
                "Выгруженный чанк не перезаливается сразу: иначе"
                        + " очередь будет крутиться вокруг отсутствующего мира. При смене правила перепроверь refill.");
    }
}
