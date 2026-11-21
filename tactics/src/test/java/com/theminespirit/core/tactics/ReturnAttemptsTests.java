package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReturnAttemptsTests {
    @Test
    void keep_NoFailures_IsTrue() {
        assertTrue(
                ReturnAttempts.keep(0),
                "Возврат к якорю начинают без отказов: иначе запрет первого захода оставил бы свободного духа на "
                        + "месте последнего задания навсегда. При смене порога проверь первый возврат свободного духа");
    }

    @Test
    void keep_OneFailure_IsTrue() {
        assertTrue(
                ReturnAttempts.keep(1),
                "После одного отказа подход пробуют снова: одиночный отказ бывает случайным (дверь, шаг), иначе снятие "
                        + "возврата после него оставило бы духа у чужой ямы. При смене порога проверь повтор "
                        + "подхода после одного отказа: дух делает второй заход к якорю");
    }

    @Test
    void keep_TwoFailures_IsFalse() {
        assertFalse(
                ReturnAttempts.keep(2),
                "После двух отказов возврат снимают: иначе подход к недостижимому якорю строится заново каждые "
                        + "двадцать тиков — 563 построения за пять минут (окно 24), дух крутится на месте. "
                        + "При смене порога проверь живой возврат к недостижимому якорю: путь строится один раз");
    }

    @Test
    void keep_ManyFailures_IsFalse() {
        assertFalse(
                ReturnAttempts.keep(5),
                "Много отказов подряд держат возврат снятым: иначе счётчик сбрасывался бы и цикл подходов "
                        + "вернулся бы. При смене правила проверь долгий отказ у стены: дух свободен и не топчется");
    }
}
