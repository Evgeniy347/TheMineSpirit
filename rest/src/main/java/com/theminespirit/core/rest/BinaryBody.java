package com.theminespirit.core.rest;

import java.util.Objects;

public final class BinaryBody {
    private static final BinaryBody NONE = new BinaryBody(new byte[0]);
    private final byte[] bytes;

    private BinaryBody(byte[] bytes) {
        this.bytes = bytes.clone();
    }

    public static BinaryBody none() {
        return NONE;
    }

    public static BinaryBody of(byte[] bytes) {
        Objects.requireNonNull(bytes, "Картинка не может быть null: пустую задают none()");
        return new BinaryBody(bytes);
    }

    public int size() {
        return bytes.length;
    }

    public byte[] bytes() {
        return bytes.clone();
    }
}
