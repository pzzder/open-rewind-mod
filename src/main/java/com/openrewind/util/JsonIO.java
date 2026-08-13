package com.openrewind.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.openrewind.OpenRewind;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;

/**
 * Tiny JSON helper around Gson (bundled with Minecraft), used for recording
 * metadata sidecars and editor project files.
 */
public final class JsonIO {

    private JsonIO() { }

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    public static void write(File file, Object obj) {
        try (Writer w = new FileWriter(file)) {
            GSON.toJson(obj, w);
        } catch (Exception e) {
            OpenRewind.logger.error("[OpenRewind] failed to write json " + file, e);
        }
    }

    public static <T> T read(File file, Class<T> type) {
        if (!file.exists()) return null;
        try (Reader r = new FileReader(file)) {
            return GSON.fromJson(r, type);
        } catch (Exception e) {
            OpenRewind.logger.error("[OpenRewind] failed to read json " + file, e);
            return null;
        }
    }
}
