package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.theminespirit.core.policy.api.PolicyId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BlockProtectionListTests {
    private BlockProtection protection;

    @BeforeAll
    void loadProtection() {
        protection = ProtectionConfig.readClasspath();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "minecraft:oak_sign",
                "minecraft:oak_hanging_sign",
                "minecraft:birch_door",
                "minecraft:iron_door",
                "minecraft:iron_trapdoor",
                "minecraft:oak_fence",
                "minecraft:nether_brick_fence",
                "minecraft:oak_fence_gate",
                "minecraft:stone_pressure_plate",
                "minecraft:lever",
                "minecraft:stone_button",
                "minecraft:bell",
                "minecraft:lectern",
                "minecraft:jukebox",
                "minecraft:note_block",
                "minecraft:redstone_wire",
                "minecraft:redstone_torch",
                "minecraft:rail",
                "minecraft:powered_rail",
                "minecraft:torch",
                "minecraft:wall_torch",
                "minecraft:soul_lantern",
                "minecraft:ladder",
                "minecraft:white_banner",
                "minecraft:piston",
                "minecraft:repeater"
            })
    void check_DenyListBlock_IsRefusedAsProtectedBlock(String block) {
        BlockProtection.Decision decision =
                protection.check(block, BlockProtectionTests.forest(), new BlockProtection.Placement(false, false));

        BlockProtection.Refused refused = assertInstanceOf(
                BlockProtection.Refused.class,
                decision,
                "Блок " + block + " из чёрного списка защиты: его ломать нельзя, иначе дух разбирает механизмы "
                        + "и сигнальные блоки. При смене deny-списка проверь полный набор значений ValueSource");
        assertEquals(
                PolicyId.PROTECTED_BLOCKS,
                refused.policy(),
                "Блок " + block + ": отказ по чёрному списку называет политику PROTECTED_BLOCKS, иначе лог "
                        + "и исключения ищут другую причину. При смене порядка правил проверь PolicyId");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "minecraft:stone",
                "minecraft:dirt",
                "minecraft:grass_block",
                "minecraft:oak_log",
                "minecraft:dark_oak_leaves",
                "minecraft:sand",
                "minecraft:gravel",
                "minecraft:coal_ore",
                "minecraft:redstone_ore",
                "minecraft:deepslate_redstone_ore",
                "minecraft:nether_quartz_ore",
                "minecraft:deepslate",
                "minecraft:tuff",
                "minecraft:netherrack",
                "minecraft:short_grass",
                "minecraft:sugar_cane"
            })
    void check_NaturalBlockInNature_IsAllowed(String block) {
        BlockProtection.Decision decision = protection.check(
                block,
                BlockProtectionTests.around(0, "", "minecraft:stone"),
                new BlockProtection.Placement(false, false));

        assertEquals(
                new BlockProtection.Allowed(),
                decision,
                "Природный блок " + block
                        + " вдали от построек обязан ломаться: иначе добыча руды и рубка леса остановятся. "
                        + "При смене natural-списка проверь полный набор значений ValueSource");
    }
}
