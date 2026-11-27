package com.theminespirit.core.board.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.ConstructionBatches.ChunkCell;
import com.theminespirit.core.observation.BlockCoord;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class TownAuditRegistryTests {

    private static final ChunkCell CHUNK = new ChunkCell(0, 0);

    private static final class World implements AuditFill.Reader {
        private final Map<BlockCoord, String> cells = new LinkedHashMap<>();

        World put(int x, int y, int z, String id) {
            cells.put(new BlockCoord(x, y, z), id);
            return this;
        }

        @Override
        public @Nullable String blockAt(BlockCoord cell) {
            return cells.get(cell);
        }
    }

    @Test
    void auditRegistry_PlanWindowMoves_CachesRequeue() {
        AuditRegistry registry = new AuditRegistry(AuditBudgets.DEFAULT, 4, 4);
        World world = new World();
        world.put(0, 60, 0, "minecraft:stone");
        world.put(0, 66, 0, "minecraft:stone");

        registry.loaded(CHUNK);
        assertEquals(
                0,
                registry.caches(),
                "Чанк без уровня плана не кэшируется: иначе окно взялось бы из"
                        + " воздуха, и весь посёлок лёг бы в unknown вместо фактов. При смене порядка старта перепроверь"
                        + " вызовы в TownAuditWatch.");

        assertTrue(
                registry.plan(1, 64),
                "Первый план всегда принимается: иначе уровень остался бы неизвестен,"
                        + " и наполнение не началось бы никогда. При смене сигнала перепроверь planRevision в SettlementPlotStore.");
        assertFalse(
                registry.plan(1, 64),
                "Та же ревизия на том же уровне ничего не перестраивает: иначе кэш"
                        + " сбрасывался бы на каждом тике, и свип не закончился бы. При смене сигнала перепроверь mapRevision.");

        registry.retain(Set.of(CHUNK));
        registry.loaded(CHUNK);
        registry.fill(4096, world, 20L);
        assertEquals(
                1,
                registry.caches(),
                "Чанк находится по координатам клетки: иначе реестр отдал бы кэш соседнего чанка, и сверка"
                        + " читала бы факты не оттуда. При смене ключа перепроверь ConstructionBatches.at.");
        assertEquals(
                "minecraft:stone",
                registry.blockAt(new BlockCoord(0, 60, 0)),
                "Прочитанный факт доступен через реестр: иначе фоновая сверка не сравнила бы кэш с миром, и"
                        + " промах не всплыл бы. При смене окна перепроверь minY и layers реестра.");

        registry.plan(2, 64);
        assertEquals(
                "minecraft:stone",
                registry.blockAt(new BlockCoord(0, 60, 0)),
                "Смена ревизии на том же уровне сохраняет факты: иначе каждое редактирование карты заставляло"
                        + " бы перечитывать весь мир. При смене правила перепроверь план блока 6.");

        registry.plan(3, 70);
        assertNull(
                registry.blockAt(new BlockCoord(0, 60, 0)),
                "Окно уехало — старые факты вне окна выброшены: иначе аудит сверял бы слой 60 с ожиданиями"
                        + " нового уровня и выдал фантомную яму. При смене окна перепроверь BELOW в моде.");
        assertEquals(
                1,
                registry.waiting(),
                "Чанк вернулся в очередь наполнения: иначе кэш навсегда остался"
                        + " бы пустым, и группа висела бы BLOCKED. При смене очереди перепроверь restart.");
        registry.fill(4096, world, 30L);
        assertEquals(
                "minecraft:stone",
                registry.blockAt(new BlockCoord(0, 66, 0)),
                "После сдвига окна читается новый слой: иначе реестр перезаливал бы старое окно, и аудит"
                        + " продолжил бы сверять уровень 60 вместо 70. При смене окна перепроверь replaceAll в plan.");
        assertEquals(
                0,
                registry.waiting(),
                "Очередь очищается после полного обхода: иначе наполнение крутило"
                        + " бы чанк каждый тик, и бюджет тика был бы пуст. При смене правила перепроверь fill.");
    }

    @Test
    void auditRegistry_ChunkUnload_CellsBecomeUnknown() {
        AuditRegistry registry = new AuditRegistry(AuditBudgets.DEFAULT, 1, 1);
        World world = new World();
        world.put(2, 64, 2, "minecraft:cobblestone");
        registry.plan(1, 64);
        registry.retain(Set.of(CHUNK));
        registry.loaded(CHUNK);
        registry.fill(2048, world, 25L);
        assertEquals(
                "minecraft:cobblestone",
                registry.blockAt(new BlockCoord(2, 64, 2)),
                "До выгрузки клетка видна: иначе группа собралась бы по пустому кэшу, и дух пошёл бы на работу"
                        + " без блоков. При смене окна перепроверь layers реестра.");

        registry.unloaded(CHUNK);

        assertNull(
                registry.blockAt(new BlockCoord(2, 64, 2)),
                "Выгруженный чанк перестаёт утверждать факты: иначе кэш держал бы призрак блока, и аудит молчал"
                        + " бы при реальной яме. При смене правила перепроверь Signs.filled и AuditMiss.");
        assertEquals(
                0,
                registry.waiting(),
                "Выгруженный чанк уходит из очереди: иначе наполнение крутило бы"
                        + " мёртвый чанк до конца игры, и бюджет тика был бы съеден. При смене очереди перепроверь restart.");
    }

    @Test
    void auditRegistry_RegionEnsure_DeduplicatesAndRetains() {
        AuditRegistry registry = new AuditRegistry(AuditBudgets.DEFAULT, 1, 1);
        registry.plan(1, 64);
        registry.retain(Set.of(CHUNK, new ChunkCell(1, 0)));
        registry.ensure(CHUNK);
        registry.ensure(CHUNK);

        assertEquals(
                1,
                registry.caches(),
                "Регион ставит чанк в кэш один раз: иначе список клеток плана"
                        + " задублирует очередь, и бюджет тика уйдёт на повторное чтение. При смене очереди"
                        + " перепроверь ensure и regionChunks.");
        assertEquals(
                1,
                registry.waiting(),
                "Повторный ensure не двигает прочитанный чанк: иначе смена"
                        + " ревизии плана перезаливала бы весь регион и аудит не успел бы за такт. При смене"
                        + " правила перепроверь ensure и loaded.");

        registry.ensure(new ChunkCell(1, 0));
        registry.retain(Set.of(CHUNK));
        registry.loaded(new ChunkCell(9, 9));
        assertEquals(
                1,
                registry.caches(),
                "Чанк вне контура не попадает в кэш: иначе исследованный мир съел бы RAM целыми областями, а"
                        + " аудит сверял бы блоки вне поселения. При смене границы перепроверь loaded и retain.");
        assertEquals(
                1,
                registry.caches(),
                "Сужение региона выбрасывает лишние чанки: иначе память"
                        + " держала бы старый город, а аудит продолжал бы сверять удалённые участки. При смене"
                        + " правила перепроверь retain и TownAuditWatch.tick.");
        assertEquals(
                1,
                registry.waiting(),
                "Очередь сужается вместе с картой чанков: иначе наполнение"
                        + " крутило бы выброшенный чанк до конца игры. При смене очереди перепроверь retain.");

        registry.fill(2048, cell -> null, 5L);
        registry.ensure(CHUNK);
        assertEquals(
                0,
                registry.waiting(),
                "Готовый чанк не возвращается в очередь от ensure: иначе"
                        + " каждая ревизия карты начинала бы полный пересчёт мира. При смене правила перепроверь"
                        + " ensure после fill.");
        registry.loaded(CHUNK);
        assertEquals(
                1,
                registry.waiting(),
                "Настоящая повторная загрузка чанка перезаливает факты:"
                        + " иначе после выгрузки и загрузки кэш остался бы unknown навсегда. При смене события"
                        + " перепроверь loaded и TownAuditWatch.onChunkLoad.");
    }

    @Test
    void auditRegistry_LayerWindow_RejectsTooTallWindow() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AuditRegistry(AuditBudgets.DEFAULT, 40, 40),
                "Окно больше предела чанка невозможно: иначе память на чанк выросла бы без пользы для envelope"
                        + " плана. При смене предела перепроверь ChunkCache.MAX_LAYERS.");
        assertThrows(
                IllegalArgumentException.class,
                () -> new AuditRegistry(AuditBudgets.DEFAULT, 0, 4),
                "Под уровнем плана обязано быть хотя бы один слой: иначе плитка levelY−1 выпала бы из кэша, и"
                        + " аудит не увидел яму под улицей. При смене окна перепроверь BELOW в моде.");
        AuditRegistry registry = new AuditRegistry(AuditBudgets.DEFAULT, 4, 4);
        registry.plan(1, 64);
        assertEquals(
                60,
                registry.minY(),
                "Нижняя граница окна = levelY − below: иначе слой плитки попал бы в"
                        + " соседний индекс, и сверка улиц врала бы. При смене окна перепроверь cellAt.");
        assertEquals(
                9,
                registry.layers(),
                "Высота окна = below + above + 1: иначе верхние слои схемы остались"
                        + " бы непрочитанными. При смене формулы перепроверь конструктор ChunkCache.");
    }
}
