package com.theminespirit.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Включает системы поселения на время пачки GameTest. На GameTest-сервере доска и координатор
 * по умолчанию выключены: их глобальные тики раздают задачи и приказы духам соседних сцен
 * (флак 17.10 — чужая задача доски удерживала духа рубки в RUNNING). Размечать нужно только
 * ту пачку, которая эти системы испытывает; после пачки обе выключаются снова.
 *
 * <p>Аннотация на методе перекрывает аннотацию класса: так пачки одного класса получают разные
 * наборы систем (например, отход лагеря без координатора, а оборона лагеря с ним).
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface SettlementSystems {
    boolean board() default false;

    boolean coordinator() default false;
}
