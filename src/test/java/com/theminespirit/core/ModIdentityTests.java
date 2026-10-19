package com.theminespirit.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ModIdentityTests {
    @Test
    void modId_JavaConstant_MatchesGradleProperty() {
        assertEquals(
                ModIdentity.MOD_ID,
                System.getProperty("theminespirit.modId"),
                "ModIdentity.MOD_ID должен совпадать с mod_id в gradle.properties: из mod_id генерируется "
                        + "neoforge.mods.toml, и при расхождении NeoForge не найдёт класс с @Mod для этого мода");
    }
}
