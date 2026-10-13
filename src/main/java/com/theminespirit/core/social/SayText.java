package com.theminespirit.core.social;

import java.util.Objects;

public final class SayText {
    private SayText() {
    }

    public static String normalize(String text, int maxLength) {
        Objects.requireNonNull(text, "text не может быть null: речь берётся из параметров скрипта");
        if (maxLength < 1) {
            throw new IllegalArgumentException("Длина речи " + maxLength + " меньше 1: лимит должен позволять "
                    + "хотя бы один символ, иначе сообщение не выговорить");
        }
        String collapsed = text.replaceAll("\\p{Cntrl}+", " ").trim();
        if (collapsed.isEmpty()) {
            throw new IllegalArgumentException("Речь '" + text + "' пустая после нормализации: в чат нельзя "
                    + "отправить пустую строку, а это ошибка плана, а не молчание");
        }
        return collapsed.length() <= maxLength ? collapsed : collapsed.substring(0, maxLength);
    }
}
