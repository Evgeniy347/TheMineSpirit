package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.reload.ContentSource;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.reload.LoadedContent;
import com.theminespirit.core.reload.TableRegistry;
import com.theminespirit.core.reload.TableStatus;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;

/**
 * Единственное место регистрации данных мода в ресурс-менеджере сервера: на каждый {@code /reload} (и загрузку
 * сервера) таблицы читаются заново из мода и датапаков мира, а ошибка в любом файле оставляет прежнюю версию
 * таблицы в силе и попадает в лог. Пустой список ошибок после {@link #lastReload} — всё прочитано.
 */
public final class DataReload {
    private static final Logger LOGGER = LogUtils.getLogger();

    private DataReload() {
    }

    /** Подписка на перезагрузку данных; вызывается один раз из точки входа мода. */
    public static void register(AddReloadListenerEvent event) {
        event.addListener((barrier, resourceManager, prepareProfiler, applyProfiler, backgroundExecutor, gameExecutor) ->
                CompletableFuture
                        .runAsync(() -> reloadFrom(resourceManager), backgroundExecutor)
                        .thenCompose(barrier::wait));
    }

    public static void reloadFrom(ResourceManager resourceManager) {
        List<TableRegistry.Report> reports = DataTables.INSTANCE.reload(new ServerContent(resourceManager));
        lastReload = reports;
        for (TableRegistry.Report report : reports) {
            if (report.failed()) {
                LOGGER.error("Таблица '{}' не перезагружена, работает прежняя версия: {}", report.id(), report.error());
            } else {
                LOGGER.info("Таблица '{}' перезагружена", report.id());
            }
        }
    }

    private static volatile List<TableRegistry.Report> lastReload = List.of();

    /** Итог последней перезагрузки: ошибки видны в /spirit tables и в логе, а не молча. */
    public static List<TableRegistry.Report> lastReload() {
        return lastReload;
    }

    private static final class ServerContent implements ContentSource {
        private final ResourceManager resources;

        ServerContent(ResourceManager resources) {
            this.resources = resources;
        }

        @Override
        public Optional<LoadedContent> read(String resourcePath) {
            ResourceLocation location = locationOf(resourcePath);
            Optional<Resource> found = resources.getResource(location);
            if (found.isEmpty()) {
                return Optional.empty();
            }
            Resource resource = found.get();
            try (InputStream stream = resource.open()) {
                String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                return Optional.of(new LoadedContent(json, origin(location, resource), sourceOf(resource)));
            } catch (IOException error) {
                throw new IllegalStateException("файл '" + location + "' не прочитан: " + error.getMessage(), error);
            }
        }

        private static ResourceLocation locationOf(String resourcePath) {
            String relative = resourcePath.startsWith("data/" + ModIdentity.MOD_ID + "/")
                    ? resourcePath.substring(("data/" + ModIdentity.MOD_ID + "/").length())
                    : resourcePath;
            return ResourceLocation.fromNamespaceAndPath(ModIdentity.MOD_ID, relative);
        }

        private static String origin(ResourceLocation location, Resource resource) {
            return ModIdentity.MOD_ID + ":" + location.getPath() + " [" + resource.sourcePackId() + "]";
        }

        private static TableStatus.Source sourceOf(Resource resource) {
            return resource.knownPackInfo().isPresent() ? TableStatus.Source.MOD : TableStatus.Source.DATAPACK;
        }
    }
}