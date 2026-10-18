package com.theminespirit.neoforge.blocks;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.script.BlockPointParams;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class UseBlockScript implements ScriptInstance {
    private final SpiritEntity spirit;
    private final Level level;
    private final BlockCoord target;
    private final BlockReach reach;
    private ScriptStatus outcome = ScriptStatus.RUNNING;
    private String failureReason;
    private ScriptResult result;

    public UseBlockScript(SpiritEntity spirit, BlockPointParams params) {
        this.spirit = spirit;
        this.level = spirit.level();
        this.target = new BlockCoord(params.x(), params.y(), params.z());
        this.reach = new BlockReach(spirit, target);
    }

    @Override
    public ScriptStatus tick() {
        if (outcome != ScriptStatus.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время нажатия блока");
        }
        BlockPos pos = BlockReach.toPos(target);
        BlockState state = level.getBlockState(pos);
        if (!UsableBlocks.pressable(state)) {
            return fail("блок " + blockId(state) + " в клетке " + pos.toShortString()
                    + " не нажимается: ожидаются рычаг, кнопка или дверь");
        }
        BlockProtection.Decision allowed = spirit.protectionGate().checkUse(pos);
        if (allowed instanceof BlockProtection.Refused refused) {
            return fail(refused.reason());
        }
        if (!reach.inReach()) {
            return reach.stuck()
                    ? fail("к блоку " + pos.toShortString() + " не подойти: " + reach.refusal())
                    : ScriptStatus.RUNNING;
        }
        InteractionResult response = state.useWithoutItem(level, null, hit(pos));
        if (!response.consumesAction()) {
            return fail("блок " + blockId(state) + " в клетке " + pos.toShortString() + " не отозвался на нажатие");
        }
        outcome = ScriptStatus.SUCCEEDED;
        result = new ScriptResult.Text("блок нажат (" + blockId(state) + ")");
        return outcome;
    }

    private static BlockHitResult hit(BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }

    private static String blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    private ScriptStatus fail(String reason) {
        failureReason = reason;
        outcome = ScriptStatus.FAILED;
        reach.cancel();
        return outcome;
    }

    @Override
    public Optional<Progress> progress() {
        return Optional.of(new Progress(0, 1));
    }

    @Override
    public Optional<ScriptResult> result() {
        return Optional.ofNullable(result);
    }

    @Override
    public void cancel() {
        reach.cancel();
    }

    @Override
    public void suspend() {
        reach.suspend();
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    @Override
    public Map<String, Object> saveState() {
        return Map.of("arrived", reach.arrived());
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        reach.restoreArrived((boolean) state.getOrDefault("arrived", false));
    }
}
