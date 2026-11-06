package com.theminespirit.core.reload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.theminespirit.core.hunger.HungerRules;
import com.theminespirit.core.repair.RepairData;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SmallMechanicTableTests {
    private static final String HUNGER = """
            {"regenFoodThreshold": 18, "regenIntervalTicks": 80, "regenExhaustion": 6.0,
             "starveIntervalTicks": 80, "starveFloor": 1.0, "eatFoodThreshold": 14,
             "sprintExhaustionPerMeter": 0.1, "combatExhaustionPerAttack": 0.1,
             "digExhaustionPerBlock": 0.005, "damageExhaustionPerHit": 0.1}
            """;

    @Test
    void hunger_ReloadChangedJson_HolderReturnsNewInterval() {
        TableRegistry registry = new TableRegistry(Clock.systemUTC());
        TableHolder<HungerRules> holder = registry.registerLazy(registeredHunger(), HungerRules::defaults);
        String changed = HUNGER.replace("\"regenIntervalTicks\": 80", "\"regenIntervalTicks\": 40");
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
    void hunger_LoadDefault_ReturnsLiveHolder() {
        assertSame(
                DataTables.INSTANCE.hunger().current(),
                HungerRules.loadDefault(),
                "Потребитель обязан брать версию из держателя, а не читать classpath/константу: иначе /reload "
                        + "не поменяет ему числа, хотя таблица перечитана");
    }

    @Test
    void repair_ReloadChangedJson_HolderReturnsNewPercent() {
        TableRegistry registry = new TableRegistry(Clock.systemUTC());
        TableHolder<RepairData> holder = registry.registerLazy(registeredDefinition(), RepairData::defaults);
        ContentSource source = ContentSource.of(
                Map.of(RepairData.RESOURCE, new LoadedContent("""
                        {"maxDurabilityPercent": 50}""", "test datapack", TableStatus.Source.DATAPACK)));

        registry.reload(source);

        assertEquals(
                new RepairData(50),
                holder.current(),
                "После /reload с изменённым json держатель обязан нести новый порог: иначе датапак мира "
                        + "не поменяет правило ремонта без перезапуска, а таблица числится горячей ложно");
    }

    @Test
    void repair_LoadDefault_ReturnsLiveHolder() {
        assertSame(
                DataTables.INSTANCE.repair().current(),
                RepairData.loadDefault(),
                "Потребитель обязан брать версию из держателя, а не читать classpath/константу: иначе /reload "
                        + "не поменяет ему порог, хотя таблица перечитана");
    }

    @SuppressWarnings("unchecked")
    private static TableDefinition<RepairData> registeredDefinition() {
        for (TableDefinition<?> definition : DataTables.INSTANCE.registry().definitions()) {
            if ("repair".equals(definition.id())) {
                return (TableDefinition<RepairData>) definition;
            }
        }
        throw new AssertionError("таблица repair не зарегистрирована в DataTables: /reload её не перечитает");
    }

    @SuppressWarnings("unchecked")
    private static TableDefinition<HungerRules> registeredHunger() {
        for (TableDefinition<?> definition : DataTables.INSTANCE.registry().definitions()) {
            if ("hunger".equals(definition.id())) {
                return (TableDefinition<HungerRules>) definition;
            }
        }
        throw new AssertionError("таблица hunger не зарегистрирована в DataTables: /reload её не перечитает");
    }
}
