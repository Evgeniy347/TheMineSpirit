package com.theminespirit.neoforge.inventory;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.mining.ToolChoice;
import com.theminespirit.core.script.SwapToolParams;
import com.theminespirit.neoforge.MiningTables;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class SwapToolScript implements ScriptInstance {
    private final SpiritEntity spirit;
    private final SwapToolParams params;
    private ScriptStatus status = ScriptStatus.RUNNING;
    private String reason;
    private ScriptResult result;

    public SwapToolScript(SpiritEntity spirit, SwapToolParams params) {
        this.spirit = spirit;
        this.params = params;
    }

    @Override
    public ScriptStatus tick() {
        if (status != ScriptStatus.RUNNING) {
            return status;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез до смены инструмента");
        }
        Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(
                ResourceLocation.parse(params.block().contains(":") ? params.block() : "minecraft:" + params.block()));
        if (block.isEmpty()) {
            return fail("блок '" + params.block() + "' не зарегистрирован: инструмент выбирать не для чего");
        }
        BlockState state = block.get().defaultBlockState();
        List<ToolChoice.ToolOption> options = optionsFor(state);
        ToolChoice.Choice choice = ToolChoice.best(options, MiningTables.toolReserve());
        if (!choice.dropsAllowed()) {
            return fail("нет подходящего инструмента для блока '" + params.block()
                    + "': в руке и инвентаре нет целого инструмента, которым блок даёт дроп");
        }
        if (choice.slot() == ToolChoice.HAND || choice.slot() == ToolChoice.MAIN_HAND) {
            return succeed(heldId(), choice.slot(), choice.speed());
        }
        ItemStack picked = spirit.getInventory()
                .removeItem(
                        choice.slot(),
                        spirit.getInventory().getItem(choice.slot()).getCount());
        ItemStack hand = spirit.getMainHandItem();
        if (!hand.isEmpty()) {
            spirit.getInventory().setItem(choice.slot(), hand.copy());
        }
        spirit.setItemSlot(EquipmentSlot.MAINHAND, picked);
        spirit.setDropChance(EquipmentSlot.MAINHAND, 1.0F);
        return succeed(StackMapper.itemId(picked), choice.slot(), choice.speed());
    }

    @Override
    public void cancel() {}

    @Override
    public String failureReason() {
        return reason;
    }

    @Override
    public Optional<ScriptResult> result() {
        return Optional.ofNullable(result);
    }

    private List<ToolChoice.ToolOption> optionsFor(BlockState state) {
        boolean needsTool = state.requiresCorrectToolForDrops();
        List<ToolChoice.ToolOption> options = new ArrayList<>();
        options.add(new ToolChoice.ToolOption(
                ToolChoice.HAND, ToolChoice.HAND_SPEED, !needsTool, ToolChoice.UNWEARING, ToolChoice.UNWEARING, false));
        ItemStack hand = spirit.getMainHandItem();
        if (!hand.isEmpty()) {
            options.add(option(ToolChoice.MAIN_HAND, hand, state, needsTool));
        }
        Container inventory = spirit.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                options.add(option(slot, stack, state, needsTool));
            }
        }
        return options;
    }

    private static ToolChoice.ToolOption option(int slot, ItemStack stack, BlockState state, boolean needsTool) {
        int max = stack.isDamageableItem() ? stack.getMaxDamage() : ToolChoice.UNWEARING;
        int remaining = max > 0 ? max - stack.getDamageValue() : ToolChoice.UNWEARING;
        return new ToolChoice.ToolOption(
                slot,
                stack.getDestroySpeed(state),
                !needsTool || stack.isCorrectToolForDrops(state),
                remaining,
                max,
                stack.isEnchanted());
    }

    private String heldId() {
        ItemStack hand = spirit.getMainHandItem();
        return hand.isEmpty() ? "hand" : StackMapper.itemId(hand);
    }

    private ScriptStatus succeed(String item, int slot, double speed) {
        status = ScriptStatus.SUCCEEDED;
        result = new ScriptResult.Data(Map.of(
                "block", params.block(),
                "item", item,
                "slot", slot,
                "speed", speed));
        return status;
    }

    private ScriptStatus fail(String message) {
        status = ScriptStatus.FAILED;
        reason = message;
        return status;
    }
}
