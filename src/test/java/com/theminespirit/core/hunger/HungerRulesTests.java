package com.theminespirit.core.hunger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

class HungerRulesTests {
    private static final String VALID = """
            {"regenFoodThreshold": 18, "regenIntervalTicks": 80, "regenExhaustion": 6.0,
             "starveIntervalTicks": 80, "starveFloor": 1.0, "eatFoodThreshold": 14,
             "sprintExhaustionPerMeter": 0.1, "combatExhaustionPerAttack": 0.1,
             "digExhaustionPerBlock": 0.005, "damageExhaustionPerHit": 0.1}
            """;

    @Test
    void parse_ValidJson_MatchesDefaults() {
        assertEquals(
                HungerRules.defaults(),
                HungerRules.parse(VALID, "hunger.json"),
                "Файл данных обязан совпадать с умолчаниями: иначе быстрое ядро и адаптер расходятся в числах");
    }

    @Test
    void load_ClasspathResource_Reads() {
        assertEquals(
                HungerRules.defaults(),
                HungerRules.load(),
                "Файл data/theminespirit/hunger/hunger.json обязан читаться и совпадать с умолчаниями: "
                        + "скорости истощения — данные, а не ветки в коде");
    }

    @Test
    void reload_ChangedJson_HolderReturnsNewInterval() {
        TableRegistry registry = new TableRegistry(Clock.systemUTC());
        TableHolder<HungerRules> holder = registry.registerLazy(registeredDefinition(), HungerRules::defaults);
        String changed = VALID.replace("\"regenIntervalTicks\": 80", "\"regenIntervalTicks\": 40");
        ContentSource source = ContentSource.of(
                Map.of(HungerRules.RESOURCE, new LoadedContent(changed, "test datapack", TableStatus.Source.DATAPACK)));

        registry.reload(source);

        assertEquals(
                40,
                holder.current().regenIntervalTicks(),
                "После /reload с изменённым json держатель обязан нести новое значение: иначе датапак мира "
                        + "не поменяет интервал восстановления голода без перезапуска, а таблица числится горячей ложно");
    }

    @Test
    void loadDefault_ConsumerCall_ReturnsLiveHolder() {
        assertSame(
                DataTables.INSTANCE.hunger().current(),
                HungerRules.loadDefault(),
                "Потребитель обязан брать версию из держателя, а не читать classpath/константу: иначе /reload "
                        + "не поменяет ему числа, хотя таблица перечитана");
    }

    @SuppressWarnings("unchecked")
    private static TableDefinition<HungerRules> registeredDefinition() {
        for (TableDefinition<?> definition : DataTables.INSTANCE.registry().definitions()) {
            if ("hunger".equals(definition.id())) {
                return (TableDefinition<HungerRules>) definition;
            }
        }
        throw new AssertionError("таблица hunger не зарегистрирована в DataTables: /reload её не перечитает");
    }

    @Test
    void parse_UnknownField_Throws() {
        String json = VALID.replace("\"regenExhaustion\": 6.0", "\"regenExhaustion\": 6.0, \"вкус\": 1");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> HungerRules.parse(json, "hunger.json"),
                "Неизвестное поле ломает загрузку: опечатка в данных не должна молча ложиться в умолчания");

        assertEquals(
                true, error.getMessage().contains("вкус"), "Сообщение называет лишнее поле: " + error.getMessage());
    }

    @Test
    void parse_OutOfRangeThreshold_Throws() {
        assertThrows(
                IllegalStateException.class,
                () -> HungerRules.parse(
                        VALID.replace("\"regenFoodThreshold\": 18", "\"regenFoodThreshold\": 25"), "hunger.json"),
                "Порог восстановления выше шкалы голода отвергается: дух никогда не лечился бы");
    }

    @Test
    void parse_ZeroInterval_Throws() {
        assertThrows(
                IllegalStateException.class,
                () -> HungerRules.parse(
                        VALID.replace("\"starveIntervalTicks\": 80", "\"starveIntervalTicks\": 0"), "hunger.json"),
                "Нулевой интервал голода отвергается: урон капал бы каждый тик");
    }

    @Test
    void parse_NegativeRate_Throws() {
        assertThrows(
                IllegalStateException.class,
                () -> HungerRules.parse(
                        VALID.replace("\"combatExhaustionPerAttack\": 0.1", "\"combatExhaustionPerAttack\": -1"),
                        "hunger.json"),
                "Отрицательный расход отвергается: голод не отматывается назад");
    }
}
