package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.coordinator.CoordinatorTuning;
import com.theminespirit.core.intellect.FortifyTuning;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.fortify.FortifyRegistry;
import com.theminespirit.neoforge.fortify.FortifyService;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
@SettlementSystems(coordinator = true)
public final class FortifyScaleGameTests {
    private static final int SPIRITS = 20;
    private static final int OBSERVED_TICKS = 120;
    private static final int MAX_DECISIONS = OBSERVED_TICKS / (int) CoordinatorTuning.SNAPSHOT_INTERVAL_TICKS + 2;

    private FortifyScaleGameTests() {
    }

    @GameTest(template = "arena", batch = "fortify_scale", timeoutTicks = 300)
    public static void scale_TwentySpiritsWithThreats_DecisionsStayPerSnapshotNotPerSpirit(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        List<SpiritEntity> spirits = new ArrayList<>();
        for (int row = 0; row < 5; row++) {
            for (int column = 0; column < 4; column++) {
                SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(column + 2, 1, row + 2));
                spirit.getInventory().setItem(0, new ItemStack(Items.STONE, 64));
                spirits.add(spirit);
            }
        }
        for (int index = 0; index < FortifyTuning.FORTIFY_ENEMY_MIN + 2; index++) {
            Mob mob = TacticsScene.watcher(helper, EntityType.ZOMBIE, new BlockPos(1 + index, 1, 9));
            mob.setTarget(spirits.get(index % spirits.size()));
        }
        FortifyService service = FortifyRegistry.get(helper.getLevel());
        long startTick = helper.getLevel().getGameTime();
        int decisionsBefore = service.decisionCalls();

        helper.succeedWhen(() -> {
            long elapsed = helper.getLevel().getGameTime() - startTick;
            helper.assertTrue(elapsed >= OBSERVED_TICKS,
                    "наблюдаем " + OBSERVED_TICKS + " тиков, прошло " + elapsed);
            int decisions = service.decisionCalls() - decisionsBefore;
            helper.assertTrue(decisions > 0,
                    "решение укрепления обязано вызываться: иначе счётчик ничего не доказывает");
            helper.assertTrue(decisions <= MAX_DECISIONS,
                    "решение раз в снимок координатора, не на духа: вызовов " + decisions
                            + " при пределе " + MAX_DECISIONS + " на " + SPIRITS + " духов");
            for (SpiritEntity spirit : spirits) {
                spirit.discard();
            }
            GameTestFixtures.finish(helper);
        });
    }
}