package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class EntityRendererGuardTests {
    private static final Pattern ENTITY_TYPE_FIELD =
            Pattern.compile("DeferredHolder<EntityType<\\?>,\\s*EntityType<\\w+>>\\s+(\\w+)\\s*=");
    private static final Pattern RENDERER_REGISTRATION =
            Pattern.compile("registerEntityRenderer\\(\\s*ModEntities\\.(\\w+)\\.get\\(\\)");
    private static final String CLIENT_ONLY_SUBSCRIBER = "value = Dist.CLIENT";

    @Test
    void modEntityType_WithoutClientRenderer_IsReported() {
        Map<Path, String> sources = SourceFiles.javaUnder(SourceFiles.MAIN);

        assertEquals(List.of(), missingRenderers(entityTypes(sources.values()), renderers(sources.values())),
                "Тип сущности мода зарегистрирован (ModEntities), но клиенту не дан EntityRenderer: "
                        + "EntityRenderDispatcher.shouldRender берёт рендерер по типу и падает NPE, как только "
                        + "такая сущность попадает в дальность прорисовки — клиент игрока вылетает "
                        + "(18.10: /spirit demo house заспавнил духа рядом с игроком). Зарегистрируйте рендерер "
                        + "в SpiritClientRenderers.registerRenderers. Без рендерера");
    }

    @Test
    void rendererRegistration_OutsideClientOnlySubscriber_IsReported() {
        List<String> offenders = new ArrayList<>();
        for (Map.Entry<Path, String> source : SourceFiles.javaUnder(SourceFiles.MAIN).entrySet()) {
            if (!renderers(List.of(source.getValue())).isEmpty() && !source.getValue().contains(CLIENT_ONLY_SUBSCRIBER)) {
                offenders.add(source.getKey().toString());
            }
        }

        assertEquals(List.of(), offenders,
                "Регистрация EntityRenderer живёт только в подписчике @EventBusSubscriber(value = Dist.CLIENT): "
                        + "классы net.minecraft.client на выделенном сервере отсутствуют, и сервер упадёт при загрузке "
                        + "такого класса. Перенесите регистрацию в SpiritClientRenderers. Нарушители");
    }

    @Test
    void entityTypes_DeferredHolderField_IsFound() {
        String source = "public static final DeferredHolder<EntityType<?>, EntityType<SpiritEntity>> SPIRIT =\n"
                + "        REGISTRY.register(\"spirit\", () -> null);";

        assertEquals(Set.of("SPIRIT"), entityTypes(List.of(source)),
                "Сторож ослеп: поле DeferredHolder<EntityType<?>, EntityType<…>> обязано находиться как тип сущности");
    }

    @Test
    void renderers_RegisterEntityRendererCall_IsFound() {
        String source = "event.registerEntityRenderer(ModEntities.SPIRIT.get(), SpiritRenderer::new);";

        assertEquals(Set.of("SPIRIT"), renderers(List.of(source)),
                "Сторож ослеп: вызов registerEntityRenderer(ModEntities.X.get(), …) обязан находиться как рендерер X");
    }

    @Test
    void missingRenderers_TypeWithoutRenderer_IsReported() {
        assertEquals(List.of("GOLEM"), missingRenderers(Set.of("SPIRIT", "GOLEM"), Set.of("SPIRIT")),
                "Сторож ослеп: тип сущности без зарегистрированного рендерера обязан попасть в отчёт");
    }

    private static Set<String> entityTypes(Iterable<String> sources) {
        return matches(ENTITY_TYPE_FIELD, sources);
    }

    private static Set<String> renderers(Iterable<String> sources) {
        return matches(RENDERER_REGISTRATION, sources);
    }

    private static Set<String> matches(Pattern pattern, Iterable<String> sources) {
        Set<String> names = new TreeSet<>();
        for (String source : sources) {
            Matcher matcher = pattern.matcher(source);
            while (matcher.find()) {
                names.add(matcher.group(1));
            }
        }
        return names;
    }

    private static List<String> missingRenderers(Set<String> types, Set<String> renderers) {
        List<String> missing = new ArrayList<>(new TreeSet<>(types));
        missing.removeAll(renderers);
        return missing;
    }
}
