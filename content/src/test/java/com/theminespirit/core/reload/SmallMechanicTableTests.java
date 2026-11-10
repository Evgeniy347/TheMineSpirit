package com.theminespirit.core.reload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.theminespirit.core.hunger.HungerRules;
import com.theminespirit.core.movement.ProjectileDodge;
import com.theminespirit.core.movement.WalkOutRules;
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
    void dodge_ReloadChangedJson_HolderDisablesSkill() {
        TableRegistry registry = new TableRegistry(Clock.systemUTC());
        TableHolder<ProjectileDodge> holder = registry.registerLazy(registeredDodge(), ProjectileDodge::load);
        String changed = """
                {"enabled": false, "scanRadius": 20.0, "minReactionTicks": 3, "maxPredictionTicks": 12,
                 "cooldownTicks": 8, "stepSpeed": 0.45, "bodyMargin": 0.3, "mobClearance": 1.5}
                """;
        ContentSource source = ContentSource.of(Map.of(
                ProjectileDodge.RESOURCE, new LoadedContent(changed, "test datapack", TableStatus.Source.DATAPACK)));

        registry.reload(source);

        assertFalse(
                holder.current().enabled(),
                "После /reload с enabled:false навык уклонения обязан выключиться: иначе defcases не могут "
                        + "сравнить поведение духа с навыком и без него");
    }

    @Test
    void dodge_Current_ReadsLiveHolder() {
        assertSame(
                DataTables.INSTANCE.registry().holder("dodge").orElseThrow().current(),
                ProjectileDodge.current(),
                "Навык обязан читать версию из держателя DataTables, а не classpath: иначе /reload не выключит "
                        + "уклонение без перезапуска");
    }

    @Test
    void walkout_ReloadChangedJson_HolderReturnsNewSpread() {
        TableRegistry registry = new TableRegistry(Clock.systemUTC());
        TableHolder<WalkOutRules> holder = registry.registerLazy(registeredWalkOut(), WalkOutRules::load);
        ContentSource source = ContentSource.of(Map.of(
                WalkOutRules.RESOURCE,
                new LoadedContent("{\"surfaceSpread\": 48}", "test datapack", TableStatus.Source.DATAPACK)));

        registry.reload(source);

        assertEquals(
                48,
                holder.current().surfaceSpread(),
                "После /reload с новым surfaceSpread держатель обязан нести новое число: иначе предел проверки "
                        + "поверхности останется прежним до перезапуска, хотя walkout.json числится горячим");
    }

    @Test
    void walkout_Current_ReadsLiveHolder() {
        assertSame(
                DataTables.INSTANCE.registry().holder("walkout").orElseThrow().current(),
                WalkOutRules.current(),
                "Выход из ямы обязан читать walkout.json из держателя DataTables, а не classpath: иначе "
                        + "/reload не поменяет область проверки поверхности");
    }

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
    private static TableDefinition<ProjectileDodge> registeredDodge() {
        for (TableDefinition<?> definition : DataTables.INSTANCE.registry().definitions()) {
            if ("dodge".equals(definition.id())) {
                return (TableDefinition<ProjectileDodge>) definition;
            }
        }
        throw new AssertionError("таблица dodge не зарегистрирована в DataTables: /reload её не перечитает");
    }

    @SuppressWarnings("unchecked")
    private static TableDefinition<WalkOutRules> registeredWalkOut() {
        for (TableDefinition<?> definition : DataTables.INSTANCE.registry().definitions()) {
            if ("walkout".equals(definition.id())) {
                return (TableDefinition<WalkOutRules>) definition;
            }
        }
        throw new AssertionError("таблица walkout не зарегистрирована в DataTables: /reload её не перечитает");
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
