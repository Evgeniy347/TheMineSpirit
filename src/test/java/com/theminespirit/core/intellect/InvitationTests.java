package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InvitationTests {
    @Test
    void tick_EveryThirtySeconds_RepeatsGrow() {
        Invitation invitation = new Invitation();
        invitation.open(0);

        assertEquals(
                0,
                invitation.tick(599).repeats(),
                "До 600 тиков повторов нет: иначе игрок получит спам раньше первых 30 секунд");
        assertEquals(
                1,
                invitation.tick(600).repeats(),
                "Через 600 тиков первый повтор: вопрос висит с повтором каждые 30 секунд");
        assertEquals(
                2,
                invitation.tick(1200).repeats(),
                "Через 1200 тиков второй повтор: иначе игрок забудет про вопрос духов");
    }

    @Test
    void answer_WaitMore_ExtendsDeadline() {
        Invitation invitation = new Invitation();
        invitation.open(1000);

        InvitationState extended = invitation.answer(InvitationAnswer.WAIT_MORE, 1100);

        assertEquals(
                InvitationStage.EXTENDED,
                extended.stage(),
                "Ответ «подождите» продлевает вопрос: иначе духи уйдут, пока игрок собирается");
        assertEquals(
                1000 + 3600 + 1200,
                extended.deadline(),
                "Срок продлевается на 1200 тиков: иначе игрок не успеет собраться");
        assertEquals(
                InvitationStage.EXTENDED,
                invitation.tick(1000 + 3600).stage(),
                "Продлённый вопрос не закрывается в старый срок: иначе продление ничего не даёт");
    }

    @Test
    void answer_GoWithout_ClosesImmediately() {
        Invitation invitation = new Invitation();
        invitation.open(0);

        InvitationState closed = invitation.answer(InvitationAnswer.GO_WITHOUT, 100);

        assertEquals(
                InvitationStage.GO_WITHOUT,
                closed.stage(),
                "Ответ «идите без меня» закрывает вопрос: иначе духи ждут ушедшего игрока");
        assertTrue(closed.goWithout(), "Флаг самостоятельного похода поднят: иначе команда не двинется");
    }

    @Test
    void tick_SilenceThreeMinutes_GoesWithout() {
        Invitation invitation = new Invitation();
        invitation.open(0);

        assertEquals(
                InvitationStage.OPEN,
                invitation.tick(3599).stage(),
                "До 3600 тиков вопрос висит: иначе молчание закроет его раньше трёх минут");
        InvitationState closed = invitation.tick(3600);

        assertEquals(
                InvitationStage.GO_WITHOUT,
                closed.stage(),
                "Молчание 3 минуты — духи идут сами: иначе экспедиция встанет без ответа");
        assertTrue(closed.goWithout(), "Флаг самостоятельного похода поднят: иначе команда не двинется");
    }

    @Test
    void answer_WaitMoreBeyondLimit_DoesNotExtend() {
        Invitation fresh = new Invitation();
        fresh.open(0);
        for (int i = 0; i < IntellectTuning.loadDefault().inviteMaxExtends(); i++) {
            fresh.answer(InvitationAnswer.WAIT_MORE, 100 + i);
        }
        long cappedDeadline = fresh.answer(InvitationAnswer.WAIT_MORE, 200).deadline();
        assertEquals(
                cappedDeadline,
                fresh.answer(InvitationAnswer.WAIT_MORE, 201).deadline(),
                "Сверх лимита продление не растёт: иначе игрок может держать духов бесконечно");
        assertTrue(cappedDeadline >= 0, "Срок не уходит в минус");
    }

    @Test
    void answer_AfterClose_StateUnchanged() {
        Invitation invitation = new Invitation();
        invitation.open(0);
        InvitationState closed = invitation.tick(3600);

        InvitationState late = invitation.answer(InvitationAnswer.WAIT_MORE, 3700);

        assertEquals(
                closed,
                late,
                "Поздний клик по кнопке ничего не меняет: иначе опоздавший ответ воскресит закрытый поход");
    }

    @Test
    void tick_BeforeOpen_Throws() {
        Invitation invitation = new Invitation();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> invitation.open(-1),
                "Приглашение в отрицательном тике не открывается: время не может быть отрицательным");

        assertTrue(
                error.getMessage().contains("-1"),
                "Сообщение называет тик: при падении видно, какое время передали приглашению");
        assertThrows(
                IllegalStateException.class,
                () -> invitation.tick(0),
                "Тик без открытия невозможен: сроку и повторам не от чего считаться");
    }
}
