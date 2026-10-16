package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FishingGameTests {
    private static final int POND_MIN = 4;
    private static final int POND_MAX = 6;

    private FishingGameTests() {
    }

    // [флак] поклёвка случайна (ванильное ожидание до ~380 тиков), плюс подход к берегу — потолок с запасом
    @GameTest(template = "arena", batch = "fishing_pond", timeoutTicks = 900)
    public static void fish_RodBesidePond_ReelsInFish(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        pond(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(1, 1, 1));
        spirit.getInventory().setItem(0, lureRod(helper));
        SpiritRun.launch(helper, spirit, new ScriptCall("fish",
                Map.of("count", 1, "duration_ticks", 600, "radius", 16)), 1);

        helper.succeedWhen(() -> {
            RunStatus status = spirit.taskStatus().orElse(null);
            helper.assertTrue(status == RunStatus.SUCCEEDED,
                    "дух с удочкой у пруда обязан поймать рыбу за отведённое время: статус " + status
                            + ", причина: " + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(fishCount(spirit) >= 1,
                    "после успешной рыбалки в инвентаре обязана быть рыба: иначе ScriptResult врёт");
            spirit.discard();
            TacticsScene.finish(helper, spirit);
        });
    }

    @GameTest(template = "arena", batch = "fishing_no_rod", timeoutTicks = 200)
    public static void fish_NoRod_Refuses(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        pond(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(1, 1, 1));
        SpiritRun.launch(helper, spirit, new ScriptCall("fish",
                Map.of("count", 1, "duration_ticks", 600, "radius", 16)), 1);

        helper.succeedWhen(() -> {
            RunStatus status = spirit.taskStatus().orElse(null);
            helper.assertTrue(status == RunStatus.FAILED,
                    "без удочки рыбалка обязана отказать, статус: " + status);
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(reason.contains("удочк"),
                    "причина отказа обязана называть отсутствие удочки, фактическая: '" + reason + "'");
            spirit.discard();
            TacticsScene.finish(helper, spirit);
        });
    }

    private static void pond(GameTestHelper helper) {
        for (int x = POND_MIN; x <= POND_MAX; x++) {
            for (int z = POND_MIN; z <= POND_MAX; z++) {
                helper.setBlock(x, 1, z, Blocks.WATER);
            }
        }
    }

    private static ItemStack lureRod(GameTestHelper helper) {
        ItemStack rod = new ItemStack(Items.FISHING_ROD);
        rod.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.LURE), 3);
        return rod;
    }

    private static int fishCount(SpiritEntity spirit) {
        int total = 0;
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (stack.is(ItemTags.FISHES)) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
