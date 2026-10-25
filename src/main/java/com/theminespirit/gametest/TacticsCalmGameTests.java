package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.tactics.adapter.DecisionRecord;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TacticsCalmGameTests {
    private static final int WATCH_TICKS = 300;
    private static final int TIMEOUT_TICKS = 400;
    private static final int MAX_MODE_CHANGES = 1;

    private TacticsCalmGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = TIMEOUT_TICKS, coordinator = true)
    public static void calm_NoEnemies_ModeDoesNotFlip(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(4, 1, 4));
        TacticsScene.giveHome(helper, spirit, new BlockPos(5, 1, 5));
        spirit.getInventory().addItem(new ItemStack(Items.STONE_SWORD));
        spirit.getInventory().addItem(new ItemStack(Items.BREAD, 8));
        helper.runAfterDelay(WATCH_TICKS, () -> {
            List<DecisionRecord> modeChanges = spirit.tactics().decisions(100).stream()
                    .filter(record -> "mode".equals(record.changed()))
                    .toList();
            helper.assertTrue(
                    modeChanges.size() <= MAX_MODE_CHANGES,
                    "Без врагов режим стабилен (не больше " + MAX_MODE_CHANGES + " смены за " + WATCH_TICKS
                            + " тиков), иначе журнал решений и лог забиты флипом DEFENSIVE↔GUARD_AREA (INC-27): "
                            + modeChanges.stream()
                                    .map(r -> r.tick() + ":" + r.before() + "→" + r.after())
                                    .toList());
            TacticsScene.finish(helper, spirit);
            helper.succeed();
        });
    }
}
