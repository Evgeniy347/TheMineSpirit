package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ShellNavTests {
    @Test
    void target_KeyJFromBoard_OpensTree() {
        assertEquals(
                ScreenTabs.Tab.TREE,
                ShellNav.target(ShellNav.KEY_J, false, ScreenTabs.Tab.BOARD),
                "J обязан вести в древо с доски: иначе подсказка подвала врёт, и владелец ищет неработающую клавишу. При смене target_KeyJFromBoard_OpensTree проверь это утверждение.");
    }

    @Test
    void target_KeyKFromTree_OpensBoard() {
        assertEquals(
                ScreenTabs.Tab.BOARD,
                ShellNav.target(ShellNav.KEY_K, false, ScreenTabs.Tab.TREE),
                "K обязан вести в доску с древа: иначе переход из подсказки не работает и экран кажется зависшим. При смене target_KeyKFromTree_OpensBoard проверь это утверждение.");
    }

    @Test
    void target_SameTab_StaysNull() {
        assertNull(
                ShellNav.target(ShellNav.KEY_K, false, ScreenTabs.Tab.BOARD),
                "K на самой доске не переоткрывает экран: иначе поиск и прокрутка сбросятся от случайного нажатия. При смене target_SameTab_StaysNull проверь это утверждение.");
        assertNull(
                ShellNav.target(ShellNav.KEY_J, false, ScreenTabs.Tab.TREE),
                "J на древе остаётся закрытием, а не переоткрытием: иначе экран не закроется клавишей из подсказки. При смене target_SameTab_StaysNull проверь это утверждение.");
    }

    @Test
    void target_TextFieldFocused_StaysNull() {
        assertNull(
                ShellNav.target(ShellNav.KEY_J, true, ScreenTabs.Tab.BOARD),
                "J в фокусе поиска обязан попадать в текст: иначе ввод латинской буквы уводит на другой экран. При смене target_TextFieldFocused_StaysNull проверь это утверждение.");
    }

    @Test
    void target_OtherKey_StaysNull() {
        assertNull(
                ShellNav.target(85, false, ScreenTabs.Tab.BOARD),
                "Прочие клавиши не переключают экран: иначе стрелки и Enter перестанут работать на своих местах. При смене target_OtherKey_StaysNull проверь это утверждение.");
    }
}
