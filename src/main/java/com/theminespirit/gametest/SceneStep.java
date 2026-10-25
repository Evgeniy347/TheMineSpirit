package com.theminespirit.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Шаг укрупнённой сцены ({@link SceneSteps}): бывшая отдельная сцена. {@code template} — шаблон сцены-хозяина,
 * {@code timeoutTicks} — бюджет шага; {@code board}/{@code coordinator} — бывшая {@code @SettlementSystems} пачки,
 * {@code calmMobs} — бывший хук пачки «мобы не берут духов целью». Сторожа сцен читают шаг как {@code @GameTest}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@interface SceneStep {
    String template();

    int timeoutTicks();

    boolean board() default false;

    boolean coordinator() default false;

    boolean calmMobs() default false;
}
