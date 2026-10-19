package com.theminespirit.core.mining;

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

class DescentLevelsTests {

    @Test
    void defaults_WidenThreeBySixAndStairTwo_AreLiterals() {
        DescentLevels defaults = DescentLevels.defaults();

        assertEquals(
                3,
                defaults.widenWidth(),
                "Должная ширина расширения — 3 блока (решение владельца 15.10): факт зафиксирован литералом");
        assertEquals(
                6,
                defaults.widenHeight(),
                "Должная высота расширения — 6 блоков (решение владельца 15.10): факт зафиксирован литералом");
        assertEquals(
                2, defaults.stairWidth(), "Должная ширина ступенек — 2 блока: третий оставляют под будущие рельсы");
    }

    @Test
    void parse_ValidJson_ReturnsValues() {
        DescentLevels data = DescentLevels.parse("""
                {"widenWidth": 5, "widenHeight": 4, "stairWidth": 3}""", "test");

        assertEquals(new DescentLevels(5, 4, 3), data, "Корректный файл обязан разбираться в значения без потерь");
    }

    @Test
    void parse_EvenWidth_Throws() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> DescentLevels.parse("""
                        {"widenWidth": 4, "widenHeight": 6, "stairWidth": 2}""", "test"),
                "Чётная ширина расширения обязана отклоняться: проход симметричен центральной колонке");

        assertTrue(error.getMessage().contains("Ширина расширения"), "Отказ обязан называть ширину расширения");
    }

    @Test
    void parse_StairWidthNotLessThanWidth_Throws() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> DescentLevels.parse("""
                        {"widenWidth": 3, "widenHeight": 6, "stairWidth": 3}""", "test"),
                "Ступеньки на всех полосах обязаны отклоняться: третью оставляют под рельсы");

        assertTrue(error.getMessage().contains("Ширина ступенек"), "Отказ обязан называть ширину ступенек");
    }

    @Test
    void parse_HeightLessThanOne_Throws() {
        assertThrows(
                IllegalStateException.class,
                () -> DescentLevels.parse("""
                        {"widenWidth": 3, "widenHeight": 0, "stairWidth": 2}""", "test"),
                "Нулевая высота расширения обязана отклоняться: расширять нечем");
    }

    @Test
    void parse_MissingField_Throws() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> DescentLevels.parse("""
                        {"widenHeight": 6, "stairWidth": 2}""", "test"),
                "Файл без widenWidth обязан отклоняться до любого расширения");

        assertTrue(
                error.getMessage().contains("widenWidth"),
                "Пропуск поля обязан называть его имя: иначе ошибка выглядит как поломка мода");
    }

    @Test
    void parse_UnknownField_Throws() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> DescentLevels.parse("""
                        {"widenWidth": 3, "widenHeight": 6, "stairWidth": 2, "depth": 10}""", "test"),
                "Файл с неизвестным полем обязан отклоняться до любого расширения");

        assertTrue(
                error.getMessage().contains("неизвестное поле 'depth'"),
                "Неизвестное поле обязано отклоняться с его именем: опечатка не должна молча игнорироваться");
    }

    @Test
    void load_ClasspathResource_MatchesDefaults() {
        assertEquals(
                DescentLevels.defaults(),
                DescentLevels.load(),
                "Файл " + DescentLevels.RESOURCE + " обязан совпадать с кодовыми DEFAULTS: расхождение тихо "
                        + "изменит ширину и высоту шахты у игроков");
    }

    @Test
    void reload_ChangedJson_HolderReturnsNewWidenWidth() {
        TableRegistry registry = new TableRegistry(Clock.systemUTC());
        TableHolder<DescentLevels> holder = registry.registerLazy(registeredDefinition(), DescentLevels::load);
        String changed = """
                {"widenWidth": 5, "widenHeight": 6, "stairWidth": 2}""";
        ContentSource source = ContentSource.of(Map.of(
                DescentLevels.RESOURCE, new LoadedContent(changed, "test datapack", TableStatus.Source.DATAPACK)));

        registry.reload(source);

        assertEquals(
                5,
                holder.current().widenWidth(),
                "После /reload с изменённым json держатель обязан нести новую ширину расширения: иначе датапак "
                        + "мира не поменяет шахту без перезапуска, а таблица числится горячей ложно");
    }

    @Test
    void loadDefault_ConsumerCall_ReturnsLiveHolder() {
        assertSame(
                DataTables.INSTANCE.descentLevels().current(),
                DescentLevels.loadDefault(),
                "Потребитель обязан брать версию из держателя, а не читать classpath/константу: иначе /reload "
                        + "не поменяет ему ширину шахты, хотя таблица перечитана");
    }

    @SuppressWarnings("unchecked")
    private static TableDefinition<DescentLevels> registeredDefinition() {
        for (TableDefinition<?> definition : DataTables.INSTANCE.registry().definitions()) {
            if ("descent_levels".equals(definition.id())) {
                return (TableDefinition<DescentLevels>) definition;
            }
        }
        throw new AssertionError("таблица descent_levels не зарегистрирована в DataTables: /reload её не перечитает");
    }
}
