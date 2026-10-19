package com.theminespirit.neoforge.hunger;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.script.DrinkPotionParams;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

public final class DrinkPotionScript implements ScriptInstance {
    private final SpiritEntity spirit;
    private final DrinkPotionParams params;
    private ScriptStatus status = ScriptStatus.RUNNING;
    private String reason;
    private ScriptResult result;

    public DrinkPotionScript(SpiritEntity spirit, DrinkPotionParams params) {
        this.spirit = spirit;
        this.params = params;
    }

    @Override
    public ScriptStatus tick() {
        if (status != ScriptStatus.RUNNING) {
            return status;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез до питья");
        }
        String wanted = normalize(params.type());
        int slot = findPotion(wanted);
        if (slot < 0) {
            return fail("в инвентаре нет зелья типа '" + params.type() + "': пить нечего");
        }
        ItemStack potion = spirit.getInventory().getItem(slot);
        PotionContents contents = potion.get(DataComponents.POTION_CONTENTS);
        if (contents == null) {
            return fail("предмет в слоте " + slot + " не несёт содержимого зелья: пить нечего");
        }
        String potionId = contents.potion()
                .flatMap(Holder::unwrapKey)
                .map(key -> key.location().toString())
                .orElse("minecraft:potion");
        List<MobEffectInstance> applied = new ArrayList<>();
        for (MobEffectInstance effect : contents.getAllEffects()) {
            spirit.addEffect(new MobEffectInstance(effect));
            applied.add(effect);
        }
        if (applied.isEmpty()) {
            return fail("у зелья типа '" + params.type() + "' нет эффектов: пить незачем");
        }
        potion.shrink(1);
        status = ScriptStatus.SUCCEEDED;
        result = new ScriptResult.Data(Map.of(
                "type", params.type(),
                "potion", potionId,
                "effects", applied.size()));
        return status;
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

    private int findPotion(String wanted) {
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (stack.isEmpty() || !stack.is(Items.POTION)) {
                continue;
            }
            PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
            if (contents != null && matches(contents, wanted)) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean matches(PotionContents contents, String wanted) {
        Optional<Holder<Potion>> potion = contents.potion();
        if (potion.isPresent()) {
            String path = potion.get()
                    .unwrapKey()
                    .map(key -> key.location().getPath())
                    .orElse("");
            if (sameType(path, wanted)) {
                return true;
            }
        }
        for (MobEffectInstance effect : contents.getAllEffects()) {
            Holder<MobEffect> holder = effect.getEffect();
            String path =
                    holder.unwrapKey().map(key -> key.location().getPath()).orElse("");
            if (sameType(path, wanted)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameType(String path, String wanted) {
        return !path.isEmpty() && (path.equals(wanted) || path.endsWith("_" + wanted) || path.startsWith(wanted + "_"));
    }

    private static String normalize(String type) {
        return type.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private ScriptStatus fail(String message) {
        status = ScriptStatus.FAILED;
        reason = message;
        return status;
    }
}
