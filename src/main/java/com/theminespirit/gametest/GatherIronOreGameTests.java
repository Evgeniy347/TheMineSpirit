package com.theminespirit.gametest;

import static com.theminespirit.gametest.CraftTestSupport.cleanup;
import static com.theminespirit.gametest.CraftTestSupport.field;
import static com.theminespirit.gametest.CraftTestSupport.prepare;
import static com.theminespirit.gametest.CraftTestSupport.stack;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GatherIronOreGameTests {
    private GatherIronOreGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 200)
    public static void gatherIronOre_StonePickaxeNearby_Succeeds(GameTestHelper helper) {
        field(helper);
        helper.setBlock(3, 1, 3, Blocks.IRON_ORE);
        SpiritEntity spirit = prepare(helper, stack(Items.STONE_PICKAXE, 1));
        spirit.startTask(new ScriptCall("gather", Map.of("block", "iron_ore", "count", 1, "radius", 16)));

        helper.succeedWhen(() -> {
            RunStatus status = spirit.taskStatus().orElse(null);
            helper.assertTrue(
                    status == RunStatus.SUCCEEDED,
                    "прямой gather iron_ore обязан пройти: " + status + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    FullCycleScene.countBlocks(helper, Blocks.IRON_ORE) == 0,
                    "руда обязана быть добыта: осталось " + FullCycleScene.countBlocks(helper, Blocks.IRON_ORE));
            cleanup(helper, spirit);
        });
    }
}
