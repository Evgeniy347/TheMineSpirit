package com.theminespirit.neoforge.rest;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class RestConfig {
    public static final String FILE_NAME = "theminespirit-rest.toml";
    public static final int DEFAULT_PORT = 8765;
    public static final int MIN_PORT = 1024;
    public static final int MAX_PORT = 65535;
    public static final String DEFAULT_BIND_ADDRESS = "0.0.0.0";

    private static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.IntValue PORT;
    private static final ModConfigSpec.ConfigValue<String> BIND_ADDRESS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("rest");
        ENABLED = builder.comment("Запускать ли REST-мост для внешнего ИИ-агента при старте выделенного сервера.")
                .define("enabled", true);
        PORT = builder.comment("TCP-порт REST-моста. Авторизации нет: порт открыт всем, кто достигает адрес привязки.")
                .defineInRange("port", DEFAULT_PORT, MIN_PORT, MAX_PORT);
        BIND_ADDRESS = builder.comment(
                        "Адрес привязки. 0.0.0.0 — вся локальная сеть, 127.0.0.1 — только этот компьютер.")
                .define("bindAddress", DEFAULT_BIND_ADDRESS);
        builder.pop();
        SPEC = builder.build();
    }

    private RestConfig() {}

    public static ModConfigSpec spec() {
        return SPEC;
    }

    public static boolean enabled() {
        return ENABLED.get();
    }

    public static int port() {
        return PORT.get();
    }

    public static String bindAddress() {
        return BIND_ADDRESS.get();
    }
}
