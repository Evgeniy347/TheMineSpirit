package com.theminespirit.core.fishing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.reload.ContentSource;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.reload.LoadedContent;
import com.theminespirit.core.reload.TableDefinition;
import com.theminespirit.core.reload.TableHolder;
import com.theminespirit.core.reload.TableRegistry;
import com.theminespirit.core.reload.TableStatus;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FishingDataTests {

    @Test
    void defaults_MinWidthAndDepth_AreTwoAndOne() {
        FishingData defaults = FishingData.defaults();

        assertEquals(
                2,
                defaults.minWidth(),
                "Должная наименьшая сторона открытой воды — 2: удочку забрасывают в пятно минимум 2x2");
        assertEquals(
                1,
                defaults.minDepth(),
                "Должная наименьшая глубина — 1: на воде толщиной в один блок рыба уже водится");
    }

    @Test
    void parse_BoundaryWater_AcceptsEdgesRejectsOutside() {
        assertEquals(
                new FishingData(2, 1), FishingData.parse("""
                {"minWidth": 2, "minDepth": 1}""", "test"), "Наименьшая допустимая вода — 2x2 глубиной 1");
        assertEquals(
                new FishingData(8, 8), FishingData.parse("""
                {"minWidth": 8, "minDepth": 8}""", "test"), "Наибольшая допустимая вода — 8x8 глубиной 8");

        assertThrows(
                IllegalStateException.class,
                () -> FishingData.parse("""
                        {"minWidth": 1, "minDepth": 1}""", "test"),
                "Вода уже 2x2 обязана отклоняться: в одну клетку удочку не забросить");
        assertThrows(
                IllegalStateException.class,
                () -> FishingData.parse("""
                        {"minWidth": 9, "minDepth": 1}""", "test"),
                "Вода шире 8x8 обязана отклоняться: это уже море, а не пруд у базы");
        assertThrows(
                IllegalStateException.class,
                () -> FishingData.parse("""
                        {"minWidth": 2, "minDepth": 0}""", "test"),
                "Нулевая глубина обязана отклоняться: мокрый пол не водоём");
        assertThrows(
                IllegalStateException.class,
                () -> FishingData.parse("""
                        {"minWidth": 2, "minDepth": 9}""", "test"),
                "Глубина больше 8 обязана отклоняться: пруд глубже не бывает");
    }

    @Test
    void parse_MissingField_Throws() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> FishingData.parse("""
                        {"minDepth": 1}""", "test"),
                "Файл без minWidth обязан отклоняться до любого поведения ловли");

        assertTrue(
                error.getMessage().contains("minWidth"),
                "Пропуск minWidth обязан называть имя поля. Фактическое: " + error.getMessage());
    }

    @Test
    void parse_UnknownField_Throws() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> FishingData.parse("""
                        {"minWidth": 2, "minDepth": 1, "lureSpeed": 3}""", "test"),
                "Файл с неизвестным полем обязан отклоняться до любого поведения ловли");

        assertTrue(
                error.getMessage().contains("неизвестное поле 'lureSpeed'"),
                "Неизвестное поле обязано отклоняться с его именем. Фактическое: " + error.getMessage());
    }

    @Test
    void defaults_WorkPointAndBucketAndSmoker_AreDataDefaults() {
        FishingData defaults = FishingData.defaults();

        assertEquals(5, defaults.workWidth(), "Рабочая точка у воды — 5x5: этому числу учат данные, а не код рыбалки");
        assertEquals(
                2, defaults.workDepth(), "Рабочая точка у воды глубиной 2: мелкую лужу на постоянное место не ставят");
        assertEquals(
                8, defaults.bucketCount(), "За один заход ведром ловят не больше 8 рыб: предел приходит из данных");
        assertEquals(16, defaults.smokerFish(), "Коптильню крафтят с 16 сырой рыбы: порог приходит из данных");
    }

    @Test
    void parse_MissingWorkFields_FallsBackToDataDefaults() {
        assertEquals(
                FishingData.defaults(),
                FishingData.parse("""
                {"minWidth": 2, "minDepth": 1}""", "test"),
                "Без новых полей файл рыбалки обязан брать умолчания: старый датапак мира не ломает рыбалку");
    }

    @Test
    void parse_WorkPointFields_Accepted() {
        assertEquals(
                new FishingData(2, 1, 6, 3, 4, 32),
                FishingData.parse("""
                {"minWidth": 2, "minDepth": 1, "workWidth": 6, "workDepth": 3, "bucketCount": 4,
                 "smokerFish": 32}""", "test"),
                "Новые поля рыбалки обязаны читаться из файла: иначе числа не правятся датапаком");
    }

    @Test
    void parse_WorkPointOutOfRange_Throws() {
        assertThrows(
                IllegalStateException.class,
                () -> FishingData.parse("""
                        {"minWidth": 2, "minDepth": 1, "workWidth": 1}""", "test"),
                "Рабочая точка уже 2x2 обязана отклоняться: у такой воды не встать и не забросить");
        assertThrows(
                IllegalStateException.class,
                () -> FishingData.parse("""
                        {"minWidth": 2, "minDepth": 1, "bucketCount": 0}""", "test"),
                "Нулевой предел рыбы в вёдрах обязан отклоняться: заход без ловли — пустая задача");
    }

    @Test
    void load_ClasspathResource_MatchesDefaults() {
        assertEquals(
                FishingData.defaults(),
                FishingData.load(),
                "Файл " + FishingData.RESOURCE + " обязан совпадать с кодовыми DEFAULTS: расхождение тихо "
                        + "изменит, какую воду дух считает пригодной для рыбалки");
    }

    @Test
    void reload_ChangedJson_HolderReturnsNewWater() {
        TableRegistry registry = new TableRegistry(Clock.systemUTC());
        TableHolder<FishingData> holder = registry.registerLazy(registeredDefinition(), FishingData::defaults);
        ContentSource source = ContentSource.of(
                Map.of(FishingData.RESOURCE, new LoadedContent("""
                        {"minWidth": 8, "minDepth": 4}""", "test datapack", TableStatus.Source.DATAPACK)));

        registry.reload(source);

        assertEquals(
                new FishingData(8, 4),
                holder.current(),
                "После /reload с изменённым json держатель обязан нести новые размеры воды: иначе датапак мира "
                        + "не поменяет требования к пруду без перезапуска, а таблица числится горячей ложно");
    }

    @Test
    void loadDefault_ConsumerCall_ReturnsLiveHolder() {
        assertSame(
                DataTables.INSTANCE.fishing().current(),
                FishingData.loadDefault(),
                "Потребитель обязан брать версию из держателя, а не читать classpath/константу: иначе /reload "
                        + "не поменяет ему числа, хотя таблица перечитана");
    }

    @SuppressWarnings("unchecked")
    private static TableDefinition<FishingData> registeredDefinition() {
        for (TableDefinition<?> definition : DataTables.INSTANCE.registry().definitions()) {
            if (definition.id().equals("fishing")) {
                return (TableDefinition<FishingData>) definition;
            }
        }
        throw new AssertionError("таблица fishing не зарегистрирована в DataTables: /reload её не перечитает");
    }
}
