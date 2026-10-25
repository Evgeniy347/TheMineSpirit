package com.theminespirit.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@interface SceneStep {
    String template();

    int timeoutTicks();

    boolean board() default false;

    boolean coordinator() default false;

    boolean calmMobs() default false;
}
