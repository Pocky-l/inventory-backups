package com.pockyl.inventorybackups.ui;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import com.pockyl.inventorybackups.InventoryBackups;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Translatable chat text with the English text as fallback. The mod usually runs on servers only; players without it
 * see the English fallback instead of raw translation keys, players with it see their own language.
 */
public final class Texts {
    private static final String ENGLISH = "/assets/" + InventoryBackups.MOD_ID + "/lang/en_us.json";
    private static Map<String, String> english;

    private Texts() {
    }

    public static MutableComponent tr(String key, Object... args) {
        return Component.translatableWithFallback(key, english().get(key), args);
    }

    private static Map<String, String> english() {
        if (english == null) {
            try (InputStream in = Texts.class.getResourceAsStream(ENGLISH)) {
                if (in == null) {
                    throw new IOException("missing " + ENGLISH);
                }
                try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    english = new Gson().fromJson(reader, new TypeToken<Map<String, String>>() { }.getType());
                }
            } catch (IOException e) {
                InventoryBackups.LOGGER.error("Could not load the English chat texts", e);
                english = Map.of();
            }
        }
        return english;
    }
}
