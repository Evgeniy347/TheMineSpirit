package com.theminespirit.core.intellect.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.coordinator.SpiritRole;
import com.theminespirit.core.intellect.ActivityIntent;
import com.theminespirit.core.intellect.FarmWork;
import com.theminespirit.core.intellect.Forage;
import com.theminespirit.core.intellect.IntellectTuning;
import com.theminespirit.core.intellect.SettlementView;
import com.theminespirit.core.intellect.SettlementViews;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ForageActivityTests {
    private static final ForageActivity FORAGE = new ForageActivity();
    private static final UUID SPIRIT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void demand_NoWayToGetFood_Zero() {
        SettlementView hungry = SettlementViews.settlementWithForage(0, 0, 64, 32, 0, 0, FarmWork.NONE,
                Forage.NONE);

        assertEquals(0.0, FORAGE.demand(hungry),
                "Голод без способа достать еду не востребует добычу: иначе дух идёт за пустым местом и падает "
                        + "на «еды нет». Способ — сундук, зрелый урожай или дичь с оружием");
    }

    @Test
    void demand_StarvingWithChestFood_AboveChop() {
        SettlementView hungry = SettlementViews.settlementWithForage(1, 0.5, 64, 32, 2, 0, FarmWork.NONE,
                new Forage("minecraft:bread", 0, ""));

        assertTrue(FORAGE.demand(hungry) > new ChopActivity().demand(hungry),
                "При голоде и еде в сундуке добыча выше рубки: иначе духи рубят лес, пока поселение голодает");
    }

    @Test
    void demand_SatedWithChestFood_Zero() {
        SettlementView sated = SettlementViews.settlementWithForage(2, 2, 64, 32, 2, 0, FarmWork.NONE,
                new Forage("minecraft:bread", 0, ""));

        assertEquals(0.0, FORAGE.demand(sated),
                "При запасе выше нормы добыча еды не востребована: иначе духи бросают другие работы ради лишней еды");
    }

    @Test
    void demand_MoreDeficit_Higher() {
        SettlementView low = SettlementViews.settlementWithForage(1, 1, 64, 32, 2, 0, FarmWork.NONE,
                new Forage("minecraft:bread", 0, ""));
        SettlementView high = SettlementViews.settlementWithForage(1, 0.5, 64, 32, 2, 0, FarmWork.NONE,
                new Forage("minecraft:bread", 0, ""));

        assertTrue(FORAGE.demand(high) > FORAGE.demand(low),
                "Чем больше дефицит еды, тем выше добыча: иначе спрос не отличает лёгкий голод от сильного");
    }

    @Test
    void demand_SmallDeficit_AboveChop() {
        SettlementView hungry = SettlementViews.settlementWithForage(1, 1, 64, 32, 2, 0, FarmWork.NONE,
                new Forage("minecraft:bread", 0, ""));

        assertTrue(FORAGE.demand(hungry) > new ChopActivity().demand(hungry),
                "Даже небольшой дефицит еды важнее рубки: иначе поселение с одной буханкой рубит лес вместо еды");
    }

    @Test
    void demand_EmptyStock_MaxDemand() {
        SettlementView empty = SettlementViews.settlementWithForage(0, 0, 64, 32, 2, 0, FarmWork.NONE,
                new Forage("minecraft:bread", 0, ""));

        assertEquals(9.0, FORAGE.demand(empty),
                "Пустой склад при населении 3 даёт максимальный пищевой спрос 9: число — в таблице IntellectTuning");
    }

    @Test
    void demand_HungryPreyWithoutWeapon_Zero() {
        SettlementView hungry = SettlementViews.settlementWithForage(0, 0, 64, 32, 0, 0, FarmWork.NONE,
                new Forage("", 3, "cow"));

        assertEquals(0.0, FORAGE.demand(hungry),
                "Без оружия дичь не добыть: скрипт hunt требует железный меч и упал бы сразу. Спрос остаётся нулевым");
    }

    @Test
    void order_ChestFood_WithdrawsIt() {
        SettlementView view = SettlementViews.settlementWithForage(0.5, 0.5, 64, 32, 2, 0, FarmWork.NONE,
                new Forage("minecraft:cooked_beef", 0, ""));

        ActivityIntent intent = FORAGE.order(SettlementViews.spirit(SPIRIT), view);

        assertEquals(Optional.of("withdraw"), intent.script(),
                "Еда в сундуке — берём её скриптом withdraw: иначе сундук стоит полный, а дух голодает");
        assertEquals("minecraft:cooked_beef", intent.params().get("item"),
                "Забираем самый сытный найденный предмет, а не первый попавшийся");
        assertEquals(IntellectTuning.FORAGE_WITHDRAW_COUNT, intent.params().get("count"),
                "Число забираемых предметов — из IntellectTuning: иначе нехватка в сундуке валит withdraw");
    }

    @Test
    void order_RipeCropsWithoutChestFood_Harvests() {
        SettlementView view = SettlementViews.settlementWithForage(0, 0, 64, 32, 0, 0, new FarmWork(4, 0, 0),
                Forage.NONE);

        ActivityIntent intent = FORAGE.order(SettlementViews.spirit(SPIRIT), view);

        assertEquals(Optional.of("harvest_crop"), intent.script(),
                "Без еды в сундуке, но со зрелым урожаем — уборка: свежая еда важнее простоя");
    }

    @Test
    void order_ChestFoodAndOtherWays_Withdraws() {
        SettlementView view = SettlementViews.settlementWithForage(0, 0, 64, 32, 1, 0, new FarmWork(4, 0, 0),
                new Forage("minecraft:bread", 2, "cow"));

        ActivityIntent intent = FORAGE.order(SettlementViews.spirit(SPIRIT), view);

        assertEquals(Optional.of("withdraw"), intent.script(),
                "Готовая еда в сундуке дешевле урожая и охоты: сперва раздаём, что уже лежит");
    }

    @Test
    void order_PreyWithWeapon_Hunts() {
        SettlementView view = SettlementViews.settlementWithForage(0, 0, 64, 32, 1, 0, FarmWork.NONE,
                new Forage("", 2, "cow"));

        ActivityIntent intent = FORAGE.order(SettlementViews.spirit(SPIRIT), view);

        assertEquals(Optional.of("hunt"), intent.script(),
                "Еды нет, рядом дичь и есть меч — охота: иначе поселение не добудет мяса");
        assertEquals("cow", intent.params().get("species"),
                "Охотимся на тот вид, который видел снимок: иначе hunt ищет не ту дичь и падает");
        assertEquals(IntellectTuning.FORAGE_HUNT_COUNT, intent.params().get("count"),
                "Число голов — из IntellectTuning: иначе охота уходит за лишней дичью");
    }

    @Test
    void order_NoWay_EmptyIntent() {
        SettlementView view = SettlementViews.settlementWithForage(0, 0, 64, 32, 0, 0, FarmWork.NONE,
                Forage.NONE);

        ActivityIntent intent = FORAGE.order(SettlementViews.spirit(SPIRIT), view);

        assertEquals(Optional.empty(), intent.script(),
                "Нет способа достать еду — пустое намерение: скрипт не запускается, дух не падает на пустом месте");
    }

    @Test
    void eligible_Defender_IsFalse() {
        SettlementView view = SettlementViews.settlementWithForage(0, 0, 64, 32, 0, 0, FarmWork.NONE,
                Forage.NONE);

        assertFalse(FORAGE.eligible(SettlementViews.spiritWithRole(SPIRIT, SpiritRole.DEFENDER), view),
                "Защитник не уходит за едой: пост он держит, добыча — дело работника");
        assertTrue(FORAGE.eligible(SettlementViews.spirit(SPIRIT), view),
                "Работник берёт добычу еды: иначе поселение останется голодным");
    }

    @Test
    void demand_NullView_Throws() {
        assertThrows(IllegalArgumentException.class, () -> FORAGE.demand(null),
                "Спрос добычи еды без обстановки не считается: зависит от еды на душу и способа");
    }

    @Test
    void order_WaterAndRod_Fishes() {
        SettlementView view = SettlementViews.settlementWithForage(0, 0, 64, 32, 0, 0, FarmWork.NONE,
                new Forage("", 0, "", true, true));

        ActivityIntent intent = FORAGE.order(SettlementViews.spirit(SPIRIT), view);

        assertEquals(Optional.of("fish"), intent.script(),
                "Рядом вода и есть удочка — дух рыбачит: иначе способ добыть еду без охоты и склада не используется");
        assertEquals(IntellectTuning.FORAGE_FISH_COUNT, intent.params().get("count"),
                "Число рыбы — из IntellectTuning: иначе цель рыбалки разъехалась бы с настройками");
        assertEquals(IntellectTuning.FORAGE_FISH_RADIUS, intent.params().get("radius"),
                "Радиус поиска воды — из IntellectTuning: иначе рыбалка ищет воду в чужом радиусе");
        assertEquals(IntellectTuning.FORAGE_FISH_DURATION, intent.params().get("duration_ticks"),
                "Время ловли — из IntellectTuning: иначе дух рыбачит то мгновение, то вечность");
    }

    @Test
    void demand_WaterAndRodWithoutFood_AboveZero() {
        SettlementView hungry = SettlementViews.settlementWithForage(0, 0, 64, 32, 0, 0, FarmWork.NONE,
                new Forage("", 0, "", true, true));

        assertTrue(FORAGE.demand(hungry) > 0,
                "Удочка и вода — способ добыть еду: без еды добыча обязана поднять спрос, иначе дух голодает стоя");
    }

    @Test
    void order_WaterWithoutRod_EmptyIntent() {
        SettlementView view = SettlementViews.settlementWithForage(0, 0, 64, 32, 0, 0, FarmWork.NONE,
                new Forage("", 0, "", true, false));

        ActivityIntent intent = FORAGE.order(SettlementViews.spirit(SPIRIT), view);

        assertEquals(Optional.empty(), intent.script(),
                "Без удочки вода рыбалки не даёт: скрипт отказал бы сразу, поэтому намерения нет");
    }

    @Test
    void order_RodWithoutWater_EmptyIntent() {
        SettlementView view = SettlementViews.settlementWithForage(0, 0, 64, 32, 0, 0, FarmWork.NONE,
                new Forage("", 0, "", false, true));

        ActivityIntent intent = FORAGE.order(SettlementViews.spirit(SPIRIT), view);

        assertEquals(Optional.empty(), intent.script(),
                "Без воды удочка бесполезна: рыбалка отказала бы «нет воды», поэтому намерения нет");
    }

    @Test
    void forage_PreyWithoutSpecies_Throws() {
        assertThrows(IllegalArgumentException.class, () -> new Forage("", 1, ""),
                "Дичь без вида не описывается: охота не знает, на кого идти");
        assertThrows(IllegalArgumentException.class, () -> new Forage("", 0, "cow"),
                "Вид дичи без голов противоречив: снимок видел дичь, но не посчитал её");
    }
}
