package com.theminespirit.core.base;

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

class RespawnRulesTests {
    private static final String VALID = """
            {"delayTicks": 600}
            """;

    @Test
    void parse_ValidJson_MatchesDefaults() {
        assertEquals(
                RespawnRules.defaults(),
                RespawnRules.parse(VALID, "respawn.json"),
                "Файл данных обязан совпадать с умолчаниями: иначе быстрый JUnit и адаптер расходятся в задержке");
    }

    @Test
    void load_ClasspathResource_ReadsSixHundredTicks() {
        RespawnRules rules = RespawnRules.load();

        assertEquals(
                600, rules.delayTicks(), "Задержка возрождения — данные: json обязан задавать 600 тиков, а не код");
    }

    @Test
    void reload_ChangedJson_HolderReturnsNewDelay() {
        TableRegistry registry = new TableRegistry(Clock.systemUTC());
        TableHolder<RespawnRules> holder = registry.registerLazy(registeredDefinition(), RespawnRules::defaults);
        String changed = VALID.replace("600", "40");
        ContentSource source = ContentSource.of(Map.of(
                RespawnRules.RESOURCE, new LoadedContent(changed, "test datapack", TableStatus.Source.DATAPACK)));

        registry.reload(source);

        assertEquals(
                40,
                holder.current().delayTicks(),
                "После /reload с изменённым json держатель обязан нести новую задержку: иначе датапак мира не "
                        + "поменяет срок возрождения без перезапуска");
    }

    @Test
    void loadDefault_ConsumerCall_ReturnsLiveHolder() {
        assertSame(
                DataTables.INSTANCE.respawn().current(),
                RespawnRules.loadDefault(),
                "Потребитель обязан брать версию из держателя: иначе /reload не поменяет ему задержку");
    }

    @Test
    void parse_UnknownField_Throws() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> RespawnRules.parse(VALID.replace("600", "600, \"вкус\": 1"), "respawn.json"),
                "Неизвестное поле ломает загрузку: опечатка в данных не должна молча ложиться в умолчания");

        assertTrue(
                error.getMessage().contains("вкус"),
                "Сообщение обязано назвать лишнее поле, чтобы ошибку в json было видно: " + error.getMessage());
    }

    @Test
    void parse_NonPositiveDelay_Throws() {
        assertThrows(
                IllegalStateException.class,
                () -> RespawnRules.parse(VALID.replace("600", "0"), "respawn.json"),
                "Нулевая задержка отвергается: дух не может возродиться в тот же тик, что погиб");
    }

    @SuppressWarnings("unchecked")
    private static TableDefinition<RespawnRules> registeredDefinition() {
        for (TableDefinition<?> definition : DataTables.INSTANCE.registry().definitions()) {
            if ("respawn".equals(definition.id())) {
                return (TableDefinition<RespawnRules>) definition;
            }
        }
        throw new AssertionError("таблица respawn не зарегистрирована в DataTables: /reload её не перечитает");
    }
}
