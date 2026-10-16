package com.theminespirit.core.intellect.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.coordinator.SpiritRole;
import com.theminespirit.core.intellect.Activity;
import com.theminespirit.core.intellect.ActivityId;
import com.theminespirit.core.intellect.SettlementView;
import com.theminespirit.core.intellect.SettlementViews;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActivitiesTests {
    private static final UUID SPIRIT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final List<Activity> ALL = List.of(new ChopActivity(), new FarmActivity(), new ForageActivity(),
            new MineActivity(), new RestActivity(), new DefendActivity());

    private static double demandOf(ActivityId id, SettlementView view) {
        for (Activity activity : ALL) {
            if (activity.id() == id) {
                return activity.demand(view);
            }
        }
        throw new IllegalStateException("Деятельности " + id + " нет в таблице: таблица покрывает все ActivityId");
    }

    @Test
    void demand_HungrySettlement_FarmAboveChop() {
        SettlementView hungry = SettlementViews.settlement(0.5, 0.5, 64, 32, 2, 0, 0.2);

        assertTrue(demandOf(ActivityId.FARM, hungry) > demandOf(ActivityId.CHOP, hungry),
                "Голодная ферма выше рубки: иначе духи рубят лес вместо еды при пустых сундуках");
    }

    @Test
    void demand_NoOreOrWeapons_MineFirst() {
        SettlementView empty = SettlementViews.settlement(2, 2, 0, 0, 0, 0, 0.2);

        assertTrue(demandOf(ActivityId.MINE, empty) > demandOf(ActivityId.FARM, empty)
                && demandOf(ActivityId.MINE, empty) > demandOf(ActivityId.CHOP, empty)
                && demandOf(ActivityId.MINE, empty) > demandOf(ActivityId.REST, empty),
                "Без руды и оружия добыча первая: иначе поселение останется без кирок и мечей");
    }

    @Test
    void demand_ThreatsPresent_DefendFirst() {
        SettlementView danger = SettlementViews.settlement(0, 0, 0, 0, 0, 3, 0.2);

        for (ActivityId id : new ActivityId[] {ActivityId.CHOP, ActivityId.FARM, ActivityId.MINE, ActivityId.REST}) {
            assertTrue(demandOf(ActivityId.DEFEND, danger) > demandOf(id, danger),
                    "При угрозе защита выше " + id + ": иначе духи работают под ударом вместо обороны");
        }
    }

    @Test
    void demand_SatedAndStocked_RestAboveChop() {
        SettlementView calm = SettlementViews.settlement(2, 2, 64, 32, 2, 0, 0.2);

        assertTrue(demandOf(ActivityId.REST, calm) > demandOf(ActivityId.CHOP, calm),
                "Сытое поселение с запасами отдыхает, а не рубит: иначе духи работают без нужды");
    }

    @Test
    void eligible_DefenderForMine_IsFalse() {
        MineActivity mine = new MineActivity();

        assertFalse(mine.eligible(SettlementViews.spiritWithRole(SPIRIT, SpiritRole.DEFENDER),
                SettlementViews.settlement(2, 2, 0, 0, 0, 0, 0.2)),
                "Защитник не берёт добычу: пост он держит, кирка — дело работника");
        assertTrue(mine.eligible(SettlementViews.spirit(SPIRIT), SettlementViews.settlement(2, 2, 0, 0, 0, 0, 0.2)),
                "Работник берёт добычу: иначе руда не попадёт в поселение");
    }

    @Test
    void order_ScriptsMatch_ExistingScriptsOrEmpty() {
        Map<ActivityId, String> expected = Map.of(ActivityId.CHOP, "chop_tree", ActivityId.FARM, "harvest_crop",
                ActivityId.MINE, "mine_vein", ActivityId.REST, "wait");

        for (Activity activity : ALL) {
            Optional<String> script = activity.order(SettlementViews.spirit(SPIRIT), SettlementViews.empty())
                    .script();
            if (activity.id() == ActivityId.DEFEND) {
                assertEquals(Optional.empty(), script,
                        "Защита исполняется координатором: скрипта у неё нет, намерение пустое");
            } else if (activity.id() == ActivityId.FORAGE) {
                assertEquals(Optional.empty(), script,
                        "Добыча еды без способа добычи не зовёт скрипт: пустая арена — ни сундука, ни грядок, "
                                + "ни дичи, и задача упала бы на пустом месте");
            } else {
                assertEquals(Optional.of(expected.get(activity.id())), script,
                        "Намерение " + activity.id() + " ссылается на существующий скрипт: "
                                + "неизвестный скрипт дух не исполнит");
            }
        }
    }

    @Test
    void demand_NightCalm_RestHighest() {
        SettlementView night = SettlementViews.settlement(2, 2, 64, 32, 2, 0, 0.8);

        assertTrue(demandOf(ActivityId.REST, night) > demandOf(ActivityId.CHOP, night),
                "Ночью сытое поселение отдыхает: иначе духи рубят в темноте вместо сна");
        assertEquals(6.0, demandOf(ActivityId.REST, night),
                "Ночной отдых 6: выше дневного 4.5, но ниже защиты 10 при угрозе");
    }
}
