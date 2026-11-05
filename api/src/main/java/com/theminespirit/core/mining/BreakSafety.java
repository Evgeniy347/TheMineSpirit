package com.theminespirit.core.mining;

import java.util.Objects;

public sealed interface BreakSafety permits BreakSafety.Safe, BreakSafety.Unsafe {
    record Safe() implements BreakSafety {}

    record Unsafe(String reason) implements BreakSafety {
        public Unsafe {
            Objects.requireNonNull(reason, "Причина отказа не может быть null: по ней правится политика безопасности");
        }
    }
}
