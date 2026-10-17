package com.theminespirit.core.intellect.defense;

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
import com.theminespirit.core.tactics.EnemyProfile;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DefenseProfileTests {
    static final String VALID_JSON = "{"
            + "\"classes\": {"
            + "\"minecraft:creeper\": \"CREEPER\","
            + "\"minecraft:skeleton\": \"SKELETON\","
            + "\"minecraft:phantom\": \"FLYER\","
            + "\"minecraft:zombie\": \"MELEE\","
            + "\"default\": \"MELEE\"},"
            + "\"priority\": {"
            + "\"ARCHER\": [\"CREEPER\", \"SKELETON\", \"RANGED\", \"MELEE\"],"
            + "\"MELEE\": [\"MELEE\", \"RANGED\"]},"
            + "\"priorityWithoutArcher\": {"
            + "\"MELEE\": [\"SKELETON\", \"MELEE\", \"RANGED\"]}}";
    static final DefenseProfile PROFILE = DefenseProfileParser.fromJson(VALID_JSON);
    static final EnemyProfile PLAIN = new EnemyProfile("minecraft:husk", 20, 3, 0.23, false, false, false, false, 1);

    @Test
    void classify_KnownType_ReturnsMappedClass() {
        assertEquals(TargetClass.CREEPER, PROFILE.classify("minecraft:creeper"),
                "Явная карта главнее профиля: крипер обязан быть CREEPER даже при нулевом уроне в профиле");
    }

    @Test
    void classify_UnknownTypeWithoutProfile_ReturnsFallback() {
        assertEquals(TargetClass.MELEE, PROFILE.classify("minecraft:unknown_beast"),
                "Неизвестный тип без профиля падает в fallback: иначе цель останется без класса и приоритета");
    }

    @Test
    void classify_UnknownExplosiveProfile_ReturnsCreeper() {
        EnemyProfile explosive =
                new EnemyProfile("minecraft:boom", 20, 0, 0.25, false, true, false, false, 2);

        assertEquals(TargetClass.CREEPER, PROFILE.classify("minecraft:boom", explosive),
                "Взрывной враг вне карты — CREEPER: иначе лучник не возьмёт его первым, а мечник подойдёт впритык");
    }

    @Test
    void classify_UnknownFlyingProfile_ReturnsFlyer() {
        EnemyProfile flyer = new EnemyProfile("minecraft:bat", 6, 0, 0.5, false, false, false, true, 0);

        assertEquals(TargetClass.FLYER, PROFILE.classify("minecraft:bat", flyer),
                "Летающий враг вне карты — FLYER: иначе его приоритет посчитают как у пешего");
    }

    @Test
    void classify_UnknownRangedProfile_ReturnsRanged() {
        EnemyProfile shooter = new EnemyProfile("minecraft:sniper", 20, 4, 0.25, true, false, false, false, 2);

        assertEquals(TargetClass.RANGED, PROFILE.classify("minecraft:sniper", shooter),
                "Стрелок вне карты — RANGED: иначе отряд не прикроется от обстрела");
    }

    @Test
    void classify_UnknownPlainProfile_ReturnsMelee() {
        assertEquals(TargetClass.MELEE, PROFILE.classify("minecraft:husk", PLAIN),
                "Обычный враг вне карты — MELEE: иначе мечник останется без целей в ближнем бою");
    }

    @Test
    void classify_NullType_Throws() {
        assertThrows(IllegalArgumentException.class, () -> PROFILE.classify(null),
                "Классификация без типа обязана падать: иначе молчаливый fallback спрячет ошибку источника");
    }

    @Test
    void orderFor_Archer_ReturnsExactFour() {
        assertEquals(List.of(TargetClass.CREEPER, TargetClass.SKELETON, TargetClass.RANGED, TargetClass.MELEE),
                PROFILE.orderFor(DefenderRole.ARCHER, true),
                "Порядок лучника — крипер, скелет, дальние, ближние: иначе лучник бьёт ближнего вместо крипера");
    }

    @Test
    void orderFor_MeleeWithArcher_ExcludesCreeperAndSkeleton() {
        assertEquals(List.of(TargetClass.MELEE, TargetClass.RANGED),
                PROFILE.orderFor(DefenderRole.MELEE, true),
                "Мечник при живом лучнике не берёт крипера и скелета: иначе подойдёт к взрыву вместо лучника");
    }

    @Test
    void orderFor_MeleeWithoutArcher_StartsWithSkeleton() {
        List<TargetClass> order = PROFILE.orderFor(DefenderRole.MELEE, false);

        assertEquals(TargetClass.SKELETON, order.get(0),
                "Мечник без лучника идёт на скелета первым: иначе обстрел останется без ответа");
        assertEquals(List.of(TargetClass.SKELETON, TargetClass.MELEE, TargetClass.RANGED), order,
                "Порядок мечника без лучника — скелет, ближние, дальние: иначе скелет перестреляет отряд");
    }

    @Test
    void orderFor_NullRole_Throws() {
        assertThrows(IllegalArgumentException.class, () -> PROFILE.orderFor(null, true),
                "Порядок целей без роли обязан падать: иначе лучник и мечник получат чужой приоритет");
    }

    @Test
    void profile_NullClasses_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new DefenseProfile(null, TargetClass.MELEE, Map.of(DefenderRole.MELEE, List.of(TargetClass.MELEE)),
                        Map.of(DefenderRole.MELEE, List.of(TargetClass.MELEE))),
                "Профиль без карты типов обязан падать: иначе каждый враг упадёт в молчаливый fallback");
    }

    @Test
    void profile_DuplicateClassInOrder_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new DefenseProfile(Map.of("default", TargetClass.MELEE), TargetClass.MELEE,
                        Map.of(DefenderRole.ARCHER, List.of(TargetClass.MELEE, TargetClass.MELEE),
                                DefenderRole.MELEE, List.of(TargetClass.MELEE)),
                        Map.of(DefenderRole.MELEE, List.of(TargetClass.MELEE))),
                "Дубль класса в приоритете обязан падать: повтор ломает тай-брейк целей");
    }

    @Test
    void profile_EmptyOrder_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new DefenseProfile(Map.of("default", TargetClass.MELEE), TargetClass.MELEE,
                        Map.of(DefenderRole.ARCHER, List.of(), DefenderRole.MELEE, List.of(TargetClass.MELEE)),
                        Map.of(DefenderRole.MELEE, List.of(TargetClass.MELEE))),
                "Пустой порядок целей обязан падать: отряд без порядка бьёт кого попало");
    }

    @Test
    void reload_ChangedJson_HolderReturnsNewClassMapping() {
        TableRegistry registry = new TableRegistry(Clock.systemUTC());
        TableHolder<DefenseProfile> holder =
                registry.registerLazy(registeredDefinition(), DefenseProfile::readFromClasspath);
        String changed =
                VALID_JSON.replace("\"minecraft:creeper\": \"CREEPER\"", "\"minecraft:creeper\": \"MELEE\"");
        ContentSource source = ContentSource.of(Map.of(DefenseProfile.RESOURCE,
                new LoadedContent(changed, "test datapack", TableStatus.Source.DATAPACK)));

        registry.reload(source);

        assertEquals(TargetClass.MELEE, holder.current().classify("minecraft:creeper"),
                "После /reload с изменённым json держатель обязан нести новую карту классов: иначе датапак мира "
                        + "не поменяет классификацию целей без перезапуска, а таблица числится горячей ложно");
    }

    @Test
    void loadDefault_ConsumerCall_ReturnsLiveHolder() {
        assertSame(DataTables.INSTANCE.priorities().current(), DefenseProfile.loadDefault(),
                "Потребитель обязан брать версию из держателя, а не читать classpath/кэш: иначе /reload "
                        + "не поменяет ему классификацию целей, хотя таблица перечитана");
    }

    @SuppressWarnings("unchecked")
    private static TableDefinition<DefenseProfile> registeredDefinition() {
        for (TableDefinition<?> definition : DataTables.INSTANCE.registry().definitions()) {
            if (definition.id().equals("priorities")) {
                return (TableDefinition<DefenseProfile>) definition;
            }
        }
        throw new AssertionError("таблица priorities не зарегистрирована в DataTables: /reload её не перечитает");
    }
}
