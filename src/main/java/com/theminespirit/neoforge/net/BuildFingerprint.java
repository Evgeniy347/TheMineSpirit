package com.theminespirit.neoforge.net;

import com.theminespirit.TheMineSpirit;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.ModVersion;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import net.neoforged.fml.ModList;

public final class BuildFingerprint {
    private static final int HASH_CHARS = 16;
    private static volatile String cached;

    private BuildFingerprint() {}

    public static String current() {
        String local = cached;
        if (local == null) {
            local = compute();
            cached = local;
        }
        return local;
    }

    private static String compute() {
        return version() + "+" + jarHash().orElse("dev");
    }

    public static String version() {
        String version = ModList.get()
                .getModContainerById(ModIdentity.MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElseThrow(() -> new IllegalStateException(
                        "Мод " + ModIdentity.MOD_ID + " не найден в ModList: версию сборки взять неоткуда"));
        return ModVersion.parse(version).toString();
    }

    private static Optional<String> jarHash() {
        URL location = TheMineSpirit.class.getProtectionDomain().getCodeSource().getLocation();
        if (location == null) {
            return Optional.empty();
        }
        Path path;
        try {
            path = Path.of(location.toURI());
        } catch (URISyntaxException error) {
            throw new IllegalStateException("Не разобрать путь к jar мода для версии протокола: " + location, error);
        }
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 недоступен: без него нельзя сверить сборки мода", error);
        }
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Не прочитать jar мода для версии протокола: " + path, error);
        }
        return Optional.of(HexFormat.of().formatHex(digest.digest()).substring(0, HASH_CHARS));
    }
}
