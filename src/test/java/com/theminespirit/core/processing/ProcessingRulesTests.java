package com.theminespirit.core.processing;

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

class ProcessingRulesTests {
    private static final String VALID = """
            {"charcoalTarget": 16, "plankTarget": 32, "stickTarget": 32, "charcoalCount": 8,
             "cookCount": 4, "craftCount": 8, "baseDemand": 1.0, "demandScale": 8.0, "stationRadius": 16}
            """;

    @Test
    void parse_ValidJson_MatchesDefaults() {
        assertEquals(
                ProcessingRules.defaults(),
                ProcessingRules.parse(VALID, "processing.json"),
                "Файл данных обязан совпадать с умолчаниями: иначе ядро и адаптер расходятся в числах");
    }

    @Test
    void load_ClasspathResource_Reads() {
        assertEquals(
                ProcessingRules.defaults(),
                ProcessingRules.load(),
                "Файл data/theminespirit/processing/processing.json обязан читаться: цели и коэффициенты "
                        + "переработки — данные, а не ветки в коде");
    }

    @Test
    void reload_ChangedJson_HolderReturnsNewTarget() {
        TableRegistry registry = new TableRegistry(Clock.systemUTC());
        TableHolder<ProcessingRules> holder = registry.registerLazy(registeredDefinition(), ProcessingRules::defaults);
        String changed = VALID.replace("\"charcoalTarget\": 16", "\"charcoalTarget\": 24");
        ContentSource source = ContentSource.of(Map.of(
                ProcessingRules.RESOURCE, new LoadedContent(changed, "test datapack", TableStatus.Source.DATAPACK)));

        registry.reload(source);

        assertEquals(
                24,
                holder.current().charcoalTarget(),
                "После /reload с изменённым json держатель обязан нести новое значение: иначе владелец не "
                        + "поменяет цель запаса угля на лету, а таблица числится горячей ложно");
    }

    @Test
    void loadDefault_ConsumerCall_ReturnsLiveHolder() {
        assertSame(
                DataTables.INSTANCE.processing().current(),
                ProcessingRules.loadDefault(),
                "Потребитель обязан брать версию из держателя, а не читать classpath: иначе /reload "
                        + "не поменяет коэффициенты деятельности");
    }

    @Test
    void parse_UnknownField_Throws() {
        String json = VALID.replace("\"demandScale\": 8.0", "\"demandScale\": 8.0, \"вкус\": 1");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ProcessingRules.parse(json, "processing.json"),
                "Неизвестное поле ломает загрузку: опечатка в данных не должна молча ложиться в умолчания");

        assertEquals(
                true, error.getMessage().contains("вкус"), "Сообщение называет лишнее поле: " + error.getMessage());
    }

    @Test
    void parse_NegativeTarget_Throws() {
        assertThrows(
                IllegalStateException.class,
                () -> ProcessingRules.parse(
                        VALID.replace("\"plankTarget\": 32", "\"plankTarget\": -1"), "processing.json"),
                "Отрицательная цель запаса отвергается: дефицит выходил бы за границы и ломал спрос");
    }

    @Test
    void parse_CountOutOfRange_Throws() {
        assertThrows(
                IllegalStateException.class,
                () -> ProcessingRules.parse(VALID.replace("\"cookCount\": 4", "\"cookCount\": 0"), "processing.json"),
                "Нулевое число предметов за запуск отвергается: скрипт отказал бы на плане без работы");
    }

    @SuppressWarnings("unchecked")
    private static TableDefinition<ProcessingRules> registeredDefinition() {
        for (TableDefinition<?> definition : DataTables.INSTANCE.registry().definitions()) {
            if (definition.id().equals("processing")) {
                return (TableDefinition<ProcessingRules>) definition;
            }
        }
        throw new AssertionError("таблица processing не зарегистрирована в DataTables: /reload её не перечитает");
    }
}
