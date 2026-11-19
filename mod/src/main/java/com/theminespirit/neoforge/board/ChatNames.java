package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.neoforge.crafting.ItemIds;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

final class ChatNames {
    private static final Pattern TOKEN = Pattern.compile("[a-z0-9_-]+(?::[a-z0-9_/-]+)?");
    private static final String OPEN = "«";
    private static final String CLOSE = "»";

    private ChatNames() {}

    static Component display(String text, SupplyGraph graph) {
        return display(text, token -> name(token, graph));
    }

    static Component display(String text, Function<String, @Nullable Component> resolve) {
        MutableComponent shown = Component.empty();
        Matcher token = TOKEN.matcher(text);
        int from = 0;
        while (token.find()) {
            shown.append(Component.literal(text.substring(from, token.start())));
            Component name = resolve.apply(token.group());
            if (name == null) {
                shown.append(Component.literal(token.group()));
            } else {
                shown.append(Component.literal(OPEN)).append(name).append(Component.literal(CLOSE));
            }
            from = token.end();
        }
        shown.append(Component.literal(text.substring(from)));
        return shown;
    }

    private static @Nullable Component name(String token, SupplyGraph graph) {
        SupplyGraph.Building building = graph.buildings().get(token);
        if (building != null && !building.name().isBlank()) {
            return Component.literal(building.name());
        }
        return ItemIds.item(token)
                .<Component>map(item -> Component.translatable(item.getDescriptionId()))
                .orElse(null);
    }
}
