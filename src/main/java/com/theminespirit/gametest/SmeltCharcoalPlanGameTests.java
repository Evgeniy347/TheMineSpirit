package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.intellect.ActivityId;
import com.theminespirit.core.intellect.ActivityIntent;
import com.theminespirit.core.intellect.activity.SmeltActivity;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.intellect.ActivityTaskFactory;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SmeltCharcoalPlanGameTests {
    private SmeltCharcoalPlanGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 20)
    public static void charcoal_ThirteenLogsHeld_SmeltsWithoutAcquire(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        spirit.getInventory().setItem(0, new ItemStack(Items.JUNGLE_LOG, 13));
        ActivityIntent intent = new ActivityIntent(
                ActivityId.SMELT,
                Optional.of("smelt"),
                Map.of("item", "charcoal", "count", 8, "radius", 16, SmeltActivity.CHARCOAL_LOG, "jungle_log"),
                Optional.empty());
        TaskNode plan = ActivityTaskFactory.taskFor(intent, spirit).orElseThrow();
        boolean acquire = plan instanceof SequenceNode sequence
                && sequence.steps().stream()
                        .anyMatch(step -> step instanceof ScriptCall call && "acquire".equals(call.script()));
        Object count = plan instanceof SequenceNode sequence
                ? sequence.steps().stream()
                        .filter(step -> step instanceof ScriptCall call && "smelt".equals(call.script()))
                        .map(step -> ((ScriptCall) step).params().get("count"))
                        .findFirst()
                        .orElse(null)
                : null;
        spirit.discard();
        helper.assertTrue(
                !acquire && Integer.valueOf(7).equals(count),
                "13 брёвен у духа: плавка 7 угля (вход и топливо из своих), без добычи; план " + plan);
        GameTestFixtures.finish(helper);
        helper.succeed();
    }
}
