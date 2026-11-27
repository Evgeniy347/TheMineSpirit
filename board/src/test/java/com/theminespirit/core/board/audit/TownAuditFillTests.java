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
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class TownAuditFillTests {

    private static final ChunkCell CHUNK = new ChunkCell(0, 0);

    private static AuditBudgets read(String json) {
        return AuditBudgets.parse(json, AuditBudgets.RESOURCE);
    }

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
    void auditFill_BudgetSmallerThanChunk_StopsAndResumes() {
        ChunkCache cache = new ChunkCache(CHUNK, new BlockDict(), 60, 4);
        World world = new World();

        int first = AuditFill.fill(cache, 300, world, 10L);
        assertEquals(
                300,
                first,
                "Бюджет тика расходуется целиком, но не больше: иначе один чанк съест такт"
                        + " духов, и посёлок встанет без стройки. При смене лимита перепроверь fillCellsPerTick в audit.json.");
        assertEquals(
                300,
                cache.cursor(),
                "Курсор помнит прочитанное: иначе следующий тик начнёт с нуля, и чанк"
                        + " не заполнится никогда. При смене порядка обхода перепроверь cellAt и index в ChunkCache.");
        assertFalse(
                cache.scanned(),
                "Чанк не объявляется прочитанным раньше времени: иначе аудит отдаст пустоту"
                        + " как «чисто», и группа улицы уйдёт из графа. При смене правила перепроверь scanned и filled.");
        assertEquals(
                -1L,
                cache.filledTick(),
                "Тик заполнения ставится только после полного обхода: иначе"
                        + " AUDIT-MISS потеряет причинное время промаха. При смене поля перепроверь Signs.filled.");

        int second = AuditFill.fill(cache, 2000, world, 14L);
        assertEquals(
                1024 - 300,
                second,
                "Второй тик дочитывает остаток, а не весь чанк заново: иначе бюджет"
                        + " тика тратился бы вдвое, и свип поселения не успевал бы. При смене размера окна перепроверь total.");
        assertTrue(
                cache.scanned(),
                "После полного обхода чанк закрыт: иначе очередь наполнения повторяла бы"
                        + " чанк в каждом тике. При смене правила перепроверь fill в AuditRegistry.");
        assertEquals(
                14L,
                cache.filledTick(),
                "Тик заполнения не перетирается повторным вызовом: иначе инцидент"
                        + " назвал бы неверное время загрузки чанка. При смене поля перепроверь текст AUDIT-MISS.");
    }

    @Test
    void auditFill_MissingCell_KeepsUnknownInsteadOfAir() {
        ChunkCache cache = new ChunkCache(CHUNK, new BlockDict(), 60, 1);
        World world = new World();
        world.put(0, 60, 0, "minecraft:stone");

        AuditFill.fill(cache, 256, world, 5L);

        assertEquals(
                "minecraft:stone",
                cache.block(new BlockCoord(0, 60, 0)),
                "Прочитанный блок остаётся фактом: иначе сверка увидит воздух там, где камень, и дух сломает"
                        + " опору улицы. При смене словаря перепроверь BlockDict.intern и known.");
        assertNull(
                cache.block(new BlockCoord(1, 60, 0)),
                "Незагруженная клетка остаётся unknown, а не «пусто»: иначе аудит выдаст BREAK по воздуху, и"
                        + " живые застревания духов вернутся. При смене правила перепроверь AuditMiss.NOT_FILLED.");
        assertEquals(
                255,
                cache.unknown(),
                "Непрочитанные клетки считаются в unknown: иначе группа не станет"
                        + " BLOCKED после выгрузки чанка, и дух уйдёт в несуществующую работу. При смене учёта"
                        + " перепроверь unknown в ChunkCache.");
    }

    @Test
    void auditFill_VoidAboveSolid_SurfaceStopsOnSolid() {
        ChunkCache cache = new ChunkCache(CHUNK, new BlockDict(), 60, 4);
        World world = new World();
        world.put(0, 60, 0, "minecraft:stone");
        world.put(0, 61, 0, "minecraft:water");
        world.put(0, 62, 0, "minecraft:air");
        world.put(0, 63, 0, "minecraft:grass_block");

        AuditFill.fill(cache, 4096, world, 7L);

        assertEquals(
                63,
                cache.surfaceAt(0, 0),
                "Поверхность — верхний плотный блок окна: иначе вода под травой станет «землёй», и аудит"
                        + " перестанет видеть ямы под ногами духа. При смене правила перепроверь solid в BlockEquivalence.");
        assertEquals(
                ChunkCache.NO_Y,
                cache.surfaceAt(1, 0),
                "Пустой столб не имеет поверхности: иначе улица получила бы плитку на воздухе, и дух застрял"
                        + " бы на ровном месте. При смене значения перепроверь NO_Y и ExpectIndex.");
    }

    @Test
    void auditBudgets_Parse_ReadsFourLimitsAndRejectsGarbage() {
        AuditBudgets budgets = read("{\"fillCellsPerTick\":100,\"verifyCellsPerTick\":200,\"drainCellsPerTick\":30,"
                + "\"groupsPerCycle\":7}");
        assertEquals(
                100,
                budgets.fillCellsPerTick(),
                "Бюджет наполнения читается из данных: иначе лимит зашьётся"
                        + " в код, и живой сервер нельзя будет притормозить без сборки. При смене поля перепроверь"
                        + " audit.json и этот тест.");
        assertEquals(
                200,
                budgets.verifyCellsPerTick(),
                "Бюджет сверки читается из данных: иначе фоновая спираль"
                        + " получила бы константу из тактики и провалила такт духов. При смене поля перепроверь блок 6.");
        assertEquals(
                64,
                AuditBudgets.DEFAULT.groupsPerCycle(),
                "Значение по умолчанию совпадает с файлом мода:"
                        + " иначе тесты и сервер считали бы разное число групп за цикл. При смене числа перепроверь"
                        + " audit.json и DEFAULT.");

        assertThrows(
                IllegalArgumentException.class,
                () -> new AuditBudgets(0, 1, 1, 1),
                "Нулевой бюджет наполнения невозможен: иначе чанк не заполнился бы никогда, а группа осталась"
                        + " бы BLOCKED без объяснения. При смене правила перепроверь requirePositive.");
        assertThrows(
                IllegalStateException.class,
                () -> read("{\"fillCellsPerTick\":1,\"verifyCellsPerTick\":1,\"drainCellsPerTick\":1}"),
                "Каждое поле обязательно: иначе аудит тихо потерял бы лимит и начал читать мир без узды."
                        + " При смене набора полей перепроверь FIELDS и audit.json.");
        assertThrows(
                IllegalStateException.class,
                () -> read("{\"fillCellsPerTick\":1,\"verifyCellsPerTick\":1,\"drainCellsPerTick\":1,"
                        + "\"groupsPerCycle\":1,\"extra\":2}"),
                "Неизвестное поле отвергается: иначе опечатка в имени осталась бы непрочитанной, и правка лимита"
                        + " не подействовала бы. При смене полей перепроверь checkFields.");
    }
}
