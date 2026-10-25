package com.theminespirit.neoforge;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;

public final class SignTexts {
    private SignTexts() {}

    public static void write(Level level, BlockPos pos, String text) {
        Objects.requireNonNull(level, "Уровень не может быть null: без него табличку не найти");
        Objects.requireNonNull(pos, "Позиция таблички не может быть null: без неё надпись некуда писать");
        Objects.requireNonNull(text, "Текст таблички не может быть null: пустую надпись задают пустой строкой");
        BlockEntity entity = level.getBlockEntity(pos);
        if (!(entity instanceof SignBlockEntity sign)) {
            throw new IllegalStateException("В клетке " + pos.toShortString() + " нет таблички: текст '" + text
                    + "' из схемы некуда писать, проверьте блок схемы");
        }
        SignText front = sign.getFrontText().setMessage(0, Component.literal(text));
        sign.setText(front, true);
        SignText back = sign.getBackText().setMessage(0, Component.literal(text));
        sign.setText(back, false);
    }
}
