package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.script.LightPortalParams;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

final class LightPortalScript implements ScriptInstance {
    private static final double ARRIVAL = 2.5;
    private static final double REACH = 5.0;
    private static final int SLICES = 4;
    private static final int VERIFY_TICKS = 10;

    private enum Phase {
        SEARCH,
        APPROACH,
        LIGHT,
        VERIFY
    }

    private final SpiritEntity spirit;
    private final Level level;
    private final LightPortalParams params;
    private final Approach approach;
    private Phase phase = Phase.SEARCH;
    private BlockLocator.Search search;
    private PortalFrame.Spot spot;
    private int verifyTicks;
    private ScriptStatus outcome = ScriptStatus.RUNNING;
    private String failureReason;

    LightPortalScript(SpiritEntity spirit, LightPortalParams params) {
        this.spirit = spirit;
        this.level = spirit.level();
        this.params = params;
        this.approach = new Approach(spirit);
    }

    @Override
    public ScriptStatus tick() {
        if (outcome != ScriptStatus.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время зажигания портала");
        }
        return switch (phase) {
            case SEARCH -> search();
            case APPROACH -> approach();
            case LIGHT -> light();
            case VERIFY -> verify();
        };
    }

    private ScriptStatus search() {
        if (search == null) {
            spot = null;
            search = BlockLocator.search(level, spirit.blockPosition(), params.radius(), Set.of(Blocks.OBSIDIAN));
        }
        for (BlockPos found : search.step(SLICES)) {
            Optional<PortalFrame.Spot> frame = PortalFrame.at(level, found);
            if (frame.isPresent()) {
                spot = frame.orElseThrow();
                search = null;
                phase = Phase.APPROACH;
                return ScriptStatus.RUNNING;
            }
        }
        if (!search.done()) {
            return ScriptStatus.RUNNING;
        }
        search = null;
        return fail("незажжённой рамки 4×5 из обсидиана в радиусе " + params.radius() + " нет");
    }

    private ScriptStatus approach() {
        if (spot == null) {
            phase = Phase.SEARCH;
            return ScriptStatus.RUNNING;
        }
        if (!approach.isActive()) {
            approach.begin(Vec3.atCenterOf(spot.bottom()), ARRIVAL);
        }
        return switch (approach.tick()) {
            case MOVING -> ScriptStatus.RUNNING;
            case ARRIVED -> {
                phase = distanceTo(spot.bottom()) <= REACH ? Phase.LIGHT : Phase.APPROACH;
                yield ScriptStatus.RUNNING;
            }
            case STUCK, TIMEOUT -> fail("не подошёл к рамке портала: " + approach.refusal());
        };
    }

    private ScriptStatus light() {
        if (spot == null) {
            phase = Phase.SEARCH;
            return ScriptStatus.RUNNING;
        }
        int slot = flintSlot();
        if (slot < 0 && !spirit.getMainHandItem().is(Items.FLINT_AND_STEEL)) {
            return fail("нет огнива: поджечь рамку нечем");
        }
        if (distanceTo(spot.bottom()) > REACH) {
            phase = Phase.APPROACH;
            return ScriptStatus.RUNNING;
        }
        Player actor = FakePlayerFactory.getMinecraft((ServerLevel) level);
        actor.moveTo(spirit.getX(), spirit.getY(), spirit.getZ(), spirit.getYRot(), spirit.getXRot());
        ItemStack lighter = new ItemStack(Items.FLINT_AND_STEEL);
        actor.setItemInHand(InteractionHand.MAIN_HAND, lighter);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(spot.bottom()), Direction.UP, spot.bottom(), false);
        InteractionResult result = lighter.useOn(new UseOnContext(actor, InteractionHand.MAIN_HAND, hit));
        if (!result.consumesAction()) {
            return fail("огниво не зажгло рамку: огонь в клетке "
                    + spot.bottom().above().toShortString() + " не встал");
        }
        wearFlint(slot);
        verifyTicks = 0;
        phase = Phase.VERIFY;
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus verify() {
        if (spot != null && PortalFrame.isLit(level, spot)) {
            outcome = ScriptStatus.SUCCEEDED;
            return outcome;
        }
        if (++verifyTicks >= VERIFY_TICKS) {
            return fail("огонь в рамке не стал порталом: рамка не 4×5 или внутри не воздух");
        }
        return ScriptStatus.RUNNING;
    }

    private int flintSlot() {
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            if (spirit.getInventory().getItem(slot).is(Items.FLINT_AND_STEEL)) {
                return slot;
            }
        }
        return -1;
    }

    private void wearFlint(int slot) {
        if (slot < 0) {
            return;
        }
        ItemStack stack = spirit.getInventory().getItem(slot);
        stack.setDamageValue(stack.getDamageValue() + 1);
        if (stack.getDamageValue() >= stack.getMaxDamage()) {
            spirit.getInventory().setItem(slot, ItemStack.EMPTY);
        } else {
            spirit.getInventory().setItem(slot, stack);
        }
    }

    private double distanceTo(BlockPos pos) {
        return Math.sqrt(spirit.position().distanceToSqr(Vec3.atCenterOf(pos)));
    }

    private ScriptStatus fail(String reason) {
        failureReason = reason;
        outcome = ScriptStatus.FAILED;
        approach.cancel();
        return outcome;
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    @Override
    public Optional<Progress> progress() {
        return Optional.of(new Progress(phase == Phase.VERIFY ? 1 : 0, 1));
    }

    @Override
    public void cancel() {
        approach.cancel();
        outcome = ScriptStatus.CANCELLED;
    }

    @Override
    public void suspend() {
        approach.cancel();
    }

    @Override
    public Map<String, Object> saveState() {
        return Map.of(
                "phase",
                phase.name(),
                "x",
                spot == null ? 0 : spot.bottom().getX(),
                "y",
                spot == null ? 0 : spot.bottom().getY(),
                "z",
                spot == null ? 0 : spot.bottom().getZ());
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        spot = state.get("x") instanceof Number x
                        && state.get("y") instanceof Number y
                        && state.get("z") instanceof Number z
                ? new PortalFrame.Spot(new BlockPos(x.intValue(), y.intValue(), z.intValue()))
                : null;
        phase = spot == null ? Phase.SEARCH : Phase.APPROACH;
    }
}
