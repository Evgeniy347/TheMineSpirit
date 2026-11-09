package com.theminespirit.neoforge.net;

import com.theminespirit.core.ui.MapQuarry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;

final class TownMapQuarryCodec {
    private static final int MAX = 16;

    private TownMapQuarryCodec() {}

    static void write(FriendlyByteBuf buf, List<MapQuarry> all) {
        List<MapQuarry> quarries = all.subList(0, Math.min(all.size(), MAX));
        buf.writeVarInt(quarries.size());
        for (MapQuarry quarry : quarries) {
            buf.writeUtf(quarry.resource());
            buf.writeBoolean(quarry.found());
            buf.writeVarInt(quarry.percent());
            buf.writeBoolean(quarry.low());
            buf.writeBoolean(quarry.empty());
        }
    }

    static List<MapQuarry> read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX) {
            throw new IllegalArgumentException("Снимок карты несёт " + count + " мест добычи, предел " + MAX
                    + ": пакет повреждён или сборки клиента и сервера разошлись");
        }
        List<MapQuarry> quarries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            quarries.add(new MapQuarry(
                    buf.readUtf(), buf.readBoolean(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean()));
        }
        return quarries;
    }
}
