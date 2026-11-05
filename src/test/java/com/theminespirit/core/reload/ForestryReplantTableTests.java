package com.theminespirit.core.reload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.forestry.ReplantData;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ForestryReplantTableTests {
    @Test
    void reload_ChangedJson_HolderReturnsNewData() {
        TableRegistry registry = new TableRegistry(Clock.systemUTC());
        TableHolder<ReplantData> holder = registry.registerLazy(registeredDefinition(), ReplantData::defaults);
        ContentSource source = ContentSource.of(
                Map.of(ReplantData.RESOURCE, new LoadedContent("""
                        {"squareSaplings": 4, "areaRadius": 8, "areaStep": 4, "buildingGap": 3}""", "test datapack", TableStatus.Source.DATAPACK)));

        registry.reload(source);

        assertEquals(
                new ReplantData(4, 8, 4, 3),
                holder.current(),
                "После /reload с изменённым json держатель обязан нести новые числа: иначе датапак мира "
                        + "не поменяет шаг досадки без перезапуска, а таблица числится горячей ложно");
    }

    @Test
    void dataTables_ReplantSeed_MatchesClasspathDefaults() {
        assertEquals(
                ReplantData.defaults(),
                DataTables.INSTANCE.replant().current(),
                "Держатель replant обязан стартовать с файла " + ReplantData.RESOURCE + " и совпадать с DEFAULTS: "
                        + "иначе /reload и первый запуск дают разные числа");
    }

    @SuppressWarnings("unchecked")
    private static TableDefinition<ReplantData> registeredDefinition() {
        for (TableDefinition<?> definition : DataTables.INSTANCE.registry().definitions()) {
            if ("replant".equals(definition.id())) {
                return (TableDefinition<ReplantData>) definition;
            }
        }
        throw new AssertionError("таблица replant не зарегистрирована в DataTables: /reload её не перечитает");
    }
}
