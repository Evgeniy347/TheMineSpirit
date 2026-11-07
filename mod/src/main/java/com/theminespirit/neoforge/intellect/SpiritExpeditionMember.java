package com.theminespirit.neoforge.intellect;

import com.theminespirit.core.inventory.Stack;
import com.theminespirit.neoforge.PickaxeCheck;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.inventory.StackMapper;
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
        return ExpeditionKit.present(MAPPER.map(spirit.getInventory()));
    }

    @Override
    public boolean hasPickaxe() {
        return PickaxeCheck.has(spirit);
    }
}
