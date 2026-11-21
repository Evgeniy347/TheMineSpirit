package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.intellect.ExpeditionDispatch.Order;
import com.theminespirit.core.intellect.ExpeditionDispatch.Plan;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

class ExpeditionDispatchTests {
    private static final UUID LEADER = new UUID(1, 1);
    private static final UUID PARTNER = new UUID(2, 2);
    private static final UUID THIRD = new UUID(3, 3);

    @Test
    void plan_LeaderPresent_MinesAndPartnersFollow() {
        Plan plan = ExpeditionDispatch.plan(List.of(LEADER, PARTNER), LEADER);

        assertEquals(
                LEADER,
                plan.leader(),
                "Ведущий из плана похода копает первым: иначе команда ждёт вожака"
                        + " При смене plan_LeaderPresent_MinesAndPartnersFollow проверь это утверждение.");
        assertEquals(
                2,
                plan.orders().size(),
                "Каждый участник получает ровно одно задание: " + plan.orders()
                        + " Иначе тест plan_LeaderPresent_MinesAndPartnersFollow перестанет ловить эту поломку. При смене plan_LeaderPresent_MinesAndPartnersFollow проверь это утверждение.");
        assertEquals(
                ExpeditionDispatch.MINE,
                orderOf(plan, LEADER).script(),
                "Ведущий копает жилу, а не идёт за кем-то: " + plan.orders()
                        + " Иначе тест plan_LeaderPresent_MinesAndPartnersFollow перестанет ловить эту поломку. При смене plan_LeaderPresent_MinesAndPartnersFollow проверь это утверждение.");
        assertEquals(
                ExpeditionDispatch.FOLLOW,
                orderOf(plan, PARTNER).script(),
                "Напарник сопровождает ведущего духа: " + plan.orders()
                        + " Иначе тест plan_LeaderPresent_MinesAndPartnersFollow перестанет ловить эту поломку. При смене plan_LeaderPresent_MinesAndPartnersFollow проверь это утверждение.");
        assertEquals(
                LEADER.toString(),
                params(orderOf(plan, PARTNER)).get("target"),
                "Напарник догоняет ведущего по UUID, а не игрока: " + params(orderOf(plan, PARTNER))
                        + " Иначе тест plan_LeaderPresent_MinesAndPartnersFollow перестанет ловить эту поломку. При смене plan_LeaderPresent_MinesAndPartnersFollow проверь это утверждение.");
        assertEquals(
                3,
                params(orderOf(plan, PARTNER)).get("radius"),
                "Радиус сопровождения напарника 3 блока"
                        + " Иначе тест plan_LeaderPresent_MinesAndPartnersFollow перестанет ловить эту поломку. При смене plan_LeaderPresent_MinesAndPartnersFollow проверь это утверждение.");
    }

    @Test
    void plan_LeaderTask_MinesThenReturnsHome() {
        Plan plan = ExpeditionDispatch.plan(List.of(LEADER, PARTNER), LEADER);
        TaskNode task = orderOf(plan, LEADER).task();

        assertTrue(
                task instanceof SequenceNode,
                "Ведущий получает последовательность: " + task
                        + " Иначе тест plan_LeaderTask_MinesThenReturnsHome перестанет ловить эту поломку. При смене plan_LeaderTask_MinesThenReturnsHome проверь это утверждение.");
        List<TaskNode> steps = ((SequenceNode) task).steps();
        assertEquals(
                2,
                steps.size(),
                "Сначала жила, потом возврат домой: " + steps
                        + " Иначе тест plan_LeaderTask_MinesThenReturnsHome перестанет ловить эту поломку. При смене plan_LeaderTask_MinesThenReturnsHome проверь это утверждение.");
        assertEquals(
                ExpeditionDispatch.MINE,
                ((ScriptCall) steps.get(0)).script(),
                "Первый шаг — добыча жилы: " + steps
                        + " Иначе тест plan_LeaderTask_MinesThenReturnsHome перестанет ловить эту поломку. При смене plan_LeaderTask_MinesThenReturnsHome проверь это утверждение.");
        assertEquals(
                ExpeditionDispatch.RETURN_HOME,
                ((ScriptCall) steps.get(1)).script(),
                "Второй шаг — возврат домой: иначе дух останется в шахте"
                        + " При смене plan_LeaderTask_MinesThenReturnsHome проверь это утверждение.");
    }

    @Test
    void plan_LeaderMissing_FirstPresentLeads() {
        Plan plan = ExpeditionDispatch.plan(List.of(PARTNER, THIRD), LEADER);

        assertEquals(
                PARTNER,
                plan.leader(),
                "Если ведущий выбыл, первый живой участник ведёт команду: " + plan
                        + " Иначе тест plan_LeaderMissing_FirstPresentLeads перестанет ловить эту поломку. При смене plan_LeaderMissing_FirstPresentLeads проверь это утверждение.");
        assertEquals(
                ExpeditionDispatch.MINE,
                orderOf(plan, PARTNER).script(),
                "Замена ведущего копает, а не идёт за отсутствующим: " + plan.orders()
                        + " Иначе тест plan_LeaderMissing_FirstPresentLeads перестанет ловить эту поломку. При смене plan_LeaderMissing_FirstPresentLeads проверь это утверждение.");
        assertEquals(
                PARTNER.toString(),
                params(orderOf(plan, THIRD)).get("target"),
                "Остальные идут за новым ведущим: " + params(orderOf(plan, THIRD))
                        + " Иначе тест plan_LeaderMissing_FirstPresentLeads перестанет ловить эту поломку. При смене plan_LeaderMissing_FirstPresentLeads проверь это утверждение.");
    }

    @Test
    void plan_EmptyTeam_NoOrders() {
        Plan plan = ExpeditionDispatch.plan(List.of(), LEADER);

        assertEquals(
                List.of(),
                plan.orders(),
                "Пустая команда не получает заданий: иначе план врёт о выходе"
                        + " При смене plan_EmptyTeam_NoOrders проверь это утверждение. Иначе поломка пройдёт незамеченной в прогоне.");
        assertEquals(
                LEADER,
                plan.leader(),
                "Ведущий плана сохраняется: причина ожидания — набор, а не состав"
                        + " Иначе тест plan_EmptyTeam_NoOrders перестанет ловить эту поломку. При смене plan_EmptyTeam_NoOrders проверь это утверждение.");
    }

    @Test
    void plan_NullTeamOrLeader_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ExpeditionDispatch.plan(null, LEADER),
                "План без команды не строится: ведущему не за кем идти"
                        + " Иначе тест plan_NullTeamOrLeader_Throws перестанет ловить эту поломку. При смене plan_NullTeamOrLeader_Throws проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> ExpeditionDispatch.plan(List.of(LEADER), null),
                "План без ведущего не строится: напарникам не за кем идти"
                        + " Иначе тест plan_NullTeamOrLeader_Throws перестанет ловить эту поломку. При смене plan_NullTeamOrLeader_Throws проверь это утверждение.");
    }

    @Property
    void plan_AnyTeam_ExactlyOneMinerAndEverySpiritOnce(@ForAll @IntRange(min = 1, max = 16) int size) {
        List<UUID> present = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            present.add(new UUID(0, index));
        }
        UUID preferred = present.get(size / 2);

        Plan plan = ExpeditionDispatch.plan(present, preferred);

        assertEquals(
                size,
                plan.orders().size(),
                "Каждый из " + size + " духов получает задание"
                        + " Иначе тест plan_AnyTeam_ExactlyOneMinerAndEverySpiritOnce перестанет ловить эту поломку. При смене plan_AnyTeam_ExactlyOneMinerAndEverySpiritOnce проверь это утверждение.");
        assertEquals(
                preferred,
                plan.leader(),
                "Ведущий из похода остаётся ведущим плана"
                        + " Иначе тест plan_AnyTeam_ExactlyOneMinerAndEverySpiritOnce перестанет ловить эту поломку. При смене plan_AnyTeam_ExactlyOneMinerAndEverySpiritOnce проверь это утверждение.");
        Set<UUID> seen = new HashSet<>();
        int miners = 0;
        for (Order order : plan.orders()) {
            seen.add(order.spirit());
            if (ExpeditionDispatch.MINE.equals(order.script())) {
                miners++;
            } else {
                assertEquals(
                        ExpeditionDispatch.FOLLOW,
                        order.script(),
                        "Участник либо копает, либо сопровождает: " + plan.orders()
                                + " Иначе тест plan_AnyTeam_ExactlyOneMinerAndEverySpiritOnce перестанет ловить эту поломку. При смене plan_AnyTeam_ExactlyOneMinerAndEverySpiritOnce проверь это утверждение.");
                assertEquals(
                        preferred.toString(),
                        params(order).get("target"),
                        "Сопровождение ведёт к ведущему похода: " + params(order)
                                + " Иначе тест plan_AnyTeam_ExactlyOneMinerAndEverySpiritOnce перестанет ловить эту поломку. При смене plan_AnyTeam_ExactlyOneMinerAndEverySpiritOnce проверь это утверждение.");
            }
        }
        assertEquals(
                present.size(),
                seen.size(),
                "Повторных заданий одному духу нет: " + plan.orders()
                        + " Иначе тест plan_AnyTeam_ExactlyOneMinerAndEverySpiritOnce перестанет ловить эту поломку. При смене plan_AnyTeam_ExactlyOneMinerAndEverySpiritOnce проверь это утверждение.");
        assertEquals(
                1,
                miners,
                "Ведущий у команды один: иначе духи копают в разные стороны"
                        + " При смене plan_AnyTeam_ExactlyOneMinerAndEverySpiritOnce проверь это утверждение.");
    }

    private static Order orderOf(Plan plan, UUID spirit) {
        return plan.orders().stream()
                .filter(order -> order.spirit().equals(spirit))
                .findFirst()
                .orElseThrow();
    }

    private static Map<String, Object> params(Order order) {
        return ((ScriptCall) order.task()).params();
    }
}
