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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

public final class OpenScript implements ScriptInstance {
    private final SpiritEntity spirit;
    private final Level level;
    private final BlockCoord target;
    private final BlockReach reach;
    private ScriptStatus outcome = ScriptStatus.RUNNING;
    private String failureReason;
    private ScriptResult result;

    public OpenScript(SpiritEntity spirit, BlockPointParams params) {
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
            return fail("дух исчез, пока открывал блок");
        }
        BlockPos pos = BlockReach.toPos(target);
        BlockState state = level.getBlockState(pos);
        UsableBlocks.Kind kind = UsableBlocks.kind(state);
        if (kind != UsableBlocks.Kind.OPENABLE && kind != UsableBlocks.Kind.CONTAINER) {
            return fail("блок " + blockId(state) + " в клетке " + pos.toShortString()
                    + " не открывается: ожидаются дверь, люк, калитка или сундук");
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
        if (kind == UsableBlocks.Kind.CONTAINER) {
            return openContainer(pos);
        }
        return openPassage(pos, state);
    }

    private ScriptStatus openPassage(BlockPos pos, BlockState state) {
        if (state.getValue(BlockStateProperties.OPEN)) {
            return succeed("уже открыто: " + blockId(state));
        }
        level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, true), 10);
        return succeed("открыто: " + blockId(state));
    }

    private ScriptStatus openContainer(BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) {
            return fail("в клетке " + pos.toShortString() + " нет хранилища: открывать нечего");
        }
        Player opener = FakePlayerFactory.getMinecraft((ServerLevel) level);
        chest.startOpen(opener);
        chest.stopOpen(opener);
        return succeed("сундук открыт и закрыт");
    }

    private ScriptStatus succeed(String text) {
        outcome = ScriptStatus.SUCCEEDED;
        result = new ScriptResult.Text(text);
        return outcome;
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
