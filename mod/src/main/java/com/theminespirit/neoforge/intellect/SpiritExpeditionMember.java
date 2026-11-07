package com.theminespirit.neoforge.intellect;

import com.theminespirit.core.inventory.Stack;
import com.theminespirit.neoforge.PickaxeCheck;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.inventory.StackMapper;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.intellect.DescentKit;
import com.theminespirit.core.intellect.OutsideRules;
import com.theminespirit.neoforge.inventory.InventoryTables;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class SpiritExpeditionMember implements ExpeditionMember {
    private static final StackMapper MAPPER = new StackMapper();

    private final SpiritEntity spirit;

    private SpiritExpeditionMember(SpiritEntity spirit) {
        this.spirit = spirit;
    }

    public static ExpeditionMember of(SpiritEntity spirit) {
        return new SpiritExpeditionMember(spirit);
    }

    public static List<String> missingKit(List<SpiritEntity> team, StockView stock) {
        List<Stack> inventory = new ArrayList<>();
        for (SpiritEntity spirit : team) {
            inventory.addAll(MAPPER.map(spirit.getInventory()));
        }
        stock.available().forEach((item, count) -> {
            for (int left = count; left > 0; left -= 64) {
                inventory.add(new Stack(item, 0, Math.min(left, 64), 64));
            }
        });
        return DescentKit.missing(OutsideRules.loadDefault().mineKit(), inventory, InventoryTables.values());
    }

    @Override
    public UUID id() {
        return spirit.getUUID();
    }

    @Override
    public boolean removed() {
        return spirit.isRemoved();
    }

    @Override
    public String displayName() {
        return spirit.displayName();
    }

    @Override
    public Optional<String> currentScript() {
        return spirit.currentTaskScript();
    }

    @Override
    public List<Stack> inventory() {
        return List.copyOf(MAPPER.map(spirit.getInventory()));
    }

    @Override
    public boolean hasPickaxe() {
        return PickaxeCheck.has(spirit);
    }
}
