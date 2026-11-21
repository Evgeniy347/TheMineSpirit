package com.theminespirit.core.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.api.Point;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SettlementGroupsTests {
    private static final UUID FIRST = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SECOND = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final BlockCoord HOME = new BlockCoord(100, 64, -40);
    private static final BlockCoord OTHER_HOME = new BlockCoord(-7, 63, 12);
    private static final Point POS = new Point(10, 64, -7);

    private static SpiritSnapshot snapshot(UUID id) {
        return new SpiritSnapshot(
                id, POS, 1.0, SpiritRole.DEFENDER, CombatMode.DEFENSIVE, 0, false, Optional.empty(), 7, 1.0);
    }

    @Test
    void key_SameHome_SameKey() {
        assertEquals(
                SettlementGroups.keyOf(Optional.of(HOME), FIRST),
                SettlementGroups.keyOf(Optional.of(HOME), SECOND),
                "Духи с общим домом — одна группа: иначе поселение распадётся на одиночек без обороны"
                        + " При смене key_SameHome_SameKey проверь это утверждение.");
    }

    @Test
    void key_TwoHomes_KeysDiffer() {
        assertTrue(
                !SettlementGroups.keyOf(Optional.of(HOME), FIRST)
                        .equals(SettlementGroups.keyOf(Optional.of(OTHER_HOME), FIRST)),
                "Два дома — две группы: иначе координатор защищал бы чужой дом"
                        + " При смене key_TwoHomes_KeysDiffer проверь это утверждение.");
    }

    @Test
    void key_HomelessSpirit_OwnKey() {
        assertTrue(
                !SettlementGroups.keyOf(Optional.empty(), FIRST)
                        .equals(SettlementGroups.keyOf(Optional.empty(), SECOND)),
                "Дух без дома — своя одиночная группа: иначе бездомные склеились бы в чужое поселение"
                        + " При смене key_HomelessSpirit_OwnKey проверь это утверждение.");
    }

    @Test
    void key_SameHomelessSpirit_StableKey() {
        assertEquals(
                SettlementGroups.keyOf(Optional.empty(), FIRST),
                SettlementGroups.keyOf(Optional.empty(), FIRST),
                "Ключ одиночки стабилен для духа: иначе его группа пересоздавалась бы на каждом снимке"
                        + " При смене key_SameHomelessSpirit_StableKey проверь это утверждение.");
    }

    @Test
    void key_NullHome_Throws() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SettlementGroups.keyOf(null, FIRST),
                "Ключ без ответа о доме не строится: null вместо Optional склеил бы поселения молча"
                        + " Иначе тест key_NullHome_Throws перестанет ловить эту поломку. При смене key_NullHome_Throws проверь это утверждение.");
        assertTrue(
                error.getMessage().contains("home"),
                "Сообщение называет дом: без этого при падении не видно, какой ответ потерян"
                        + " При смене key_NullHome_Throws проверь это утверждение.");
    }

    @Test
    void key_HomePresent_IgnoresNullSpirit() {
        assertEquals(
                SettlementGroups.keyOf(Optional.of(HOME), FIRST),
                SettlementGroups.keyOf(Optional.of(HOME), null),
                "Дом задаёт поселение сам: поход без свободных духов не обязан падать при пустой команде"
                        + " Иначе тест key_HomePresent_IgnoresNullSpirit перестанет ловить эту поломку. При смене key_HomePresent_IgnoresNullSpirit проверь это утверждение.");
    }

    @Test
    void key_NullSpirit_Throws() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SettlementGroups.keyOf(Optional.empty(), null),
                "Одиночная группа без духа не строится: её не отличить от чужой группы"
                        + " Иначе тест key_NullSpirit_Throws перестанет ловить эту поломку. При смене key_NullSpirit_Throws проверь это утверждение.");
        assertTrue(
                error.getMessage().contains("spirit"),
                "Сообщение называет духа: без этого при падении не видно, чей ключ потерян"
                        + " При смене key_NullSpirit_Throws проверь это утверждение.");
    }

    @Test
    void partition_TwoHomes_TwoGroups() {
        Map<SettlementKey, List<SpiritSnapshot>> groups = SettlementGroups.partition(
                List.of(snapshot(FIRST), snapshot(SECOND)),
                Map.of(FIRST, Optional.of(HOME), SECOND, Optional.of(OTHER_HOME)));

        assertEquals(
                2,
                groups.size(),
                "Два дома — две группы: иначе координатор защищал бы чужой дом"
                        + " При смене partition_TwoHomes_TwoGroups проверь это утверждение.");
        assertEquals(
                List.of(snapshot(FIRST)),
                groups.get(SettlementGroups.keyOf(Optional.of(HOME), FIRST)),
                "Группа дома держит только своих духов: чужой дух в ней — приказ чужому поселению"
                        + " Иначе тест partition_TwoHomes_TwoGroups перестанет ловить эту поломку. При смене partition_TwoHomes_TwoGroups проверь это утверждение.");
        assertEquals(
                List.of(snapshot(SECOND)),
                groups.get(SettlementGroups.keyOf(Optional.of(OTHER_HOME), FIRST)),
                "Группа чужого дома держит только своих: иначе оборона перетечёт через границу поселения"
                        + " При смене partition_TwoHomes_TwoGroups проверь это утверждение.");
    }

    @Test
    void partition_HomelessSpirit_AloneInOwnGroup() {
        Map<SettlementKey, List<SpiritSnapshot>> groups =
                SettlementGroups.partition(List.of(snapshot(FIRST)), Map.of(FIRST, Optional.empty()));

        assertEquals(
                1,
                groups.size(),
                "Дух без дома — одна группа из него: иначе он потащит за собой чужие приказы"
                        + " При смене partition_HomelessSpirit_AloneInOwnGroup проверь это утверждение.");
        assertEquals(
                List.of(snapshot(FIRST)),
                groups.get(SettlementGroups.keyOf(Optional.empty(), FIRST)),
                "Одиночка виден только своему ключу: чужой дом его не защищает и не командует им"
                        + " Иначе тест partition_HomelessSpirit_AloneInOwnGroup перестанет ловить эту поломку. При смене partition_HomelessSpirit_AloneInOwnGroup проверь это утверждение.");
    }

    @Test
    void partition_MissingHomeEntry_TreatedAsHomeless() {
        Map<SettlementKey, List<SpiritSnapshot>> groups =
                SettlementGroups.partition(List.of(snapshot(FIRST)), Map.of());

        assertEquals(
                List.of(snapshot(FIRST)),
                groups.get(SettlementGroups.keyOf(Optional.empty(), FIRST)),
                "Дух без записи о доме — одиночка, а не чужое поселение: пропуск в карте не должен клеить группы"
                        + " Иначе тест partition_MissingHomeEntry_TreatedAsHomeless перестанет ловить эту поломку. При смене partition_MissingHomeEntry_TreatedAsHomeless проверь это утверждение.");
    }

    @Test
    void defendedPoint_HomeKey_PointFromHome() {
        assertEquals(
                Optional.of(new Point(HOME.x() + 0.5, HOME.y(), HOME.z() + 0.5)),
                SettlementGroups.defendedPoint(SettlementGroups.keyOf(Optional.of(HOME), FIRST)),
                "Точка обороны группы — её дом: иначе защитники встанут не у своего поселения"
                        + " При смене defendedPoint_HomeKey_PointFromHome проверь это утверждение.");
    }

    @Test
    void defendedPoint_LoneKey_Empty() {
        assertEquals(
                Optional.empty(),
                SettlementGroups.defendedPoint(SettlementGroups.keyOf(Optional.empty(), FIRST)),
                "У одиночки без дома нет точки обороны: иначе координатор оборонял бы пустоту и слал приказы зря"
                        + " При смене defendedPoint_LoneKey_Empty проверь это утверждение.");
    }

    @Test
    void defendedPoint_NullKey_Throws() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SettlementGroups.defendedPoint(null),
                "Точка обороны без ключа группы не берётся: иначе защищался бы чужой или никакой дом"
                        + " При смене defendedPoint_NullKey_Throws проверь это утверждение.");
        assertTrue(
                error.getMessage().contains("key"),
                "Сообщение называет ключ: без этого при падении не видно, чья точка потеряна"
                        + " При смене defendedPoint_NullKey_Throws проверь это утверждение.");
    }
}
