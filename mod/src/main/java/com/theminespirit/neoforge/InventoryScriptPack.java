package com.theminespirit.neoforge;

import com.theminespirit.core.script.EquipParams;
import com.theminespirit.core.script.SortChestParams;
import com.theminespirit.core.script.SwapToolParams;
import com.theminespirit.core.script.TransferParams;
import com.theminespirit.core.script.UnloadParams;
import com.theminespirit.neoforge.inventory.DepositScript;
import com.theminespirit.neoforge.inventory.EquipScript;
import com.theminespirit.neoforge.inventory.SortChestScript;
import com.theminespirit.neoforge.inventory.SwapToolScript;
import com.theminespirit.neoforge.inventory.UnloadScript;
import com.theminespirit.neoforge.inventory.WithdrawScript;

final class InventoryScriptPack extends SpiritScriptPack {
    @Override
    public String id() {
        return "inventory";
    }

    @Override
    protected void register() {
        put("swap_tool", spirit -> params -> new SwapToolScript(spirit, SwapToolParams.from(params)));
        put("deposit", spirit -> params -> new DepositScript(spirit, TransferParams.from(params)));
        put("withdraw", spirit -> params -> new WithdrawScript(spirit, TransferParams.from(params)));
        put("sort_chest", spirit -> params -> new SortChestScript(spirit, SortChestParams.from(params)));
        put("unload", spirit -> params -> new UnloadScript(spirit, UnloadParams.from(params)));
        put("equip", spirit -> params -> new EquipScript(spirit, EquipParams.from(params)));
    }
}
