package com.laker.postman.http.runtime.config;

import lombok.experimental.UtilityClass;

import java.util.function.Supplier;

@UtilityClass
public class HttpRuntimeSettingsProvider {
    private static final HttpRuntimeSettings DEFAULT_SETTINGS = new HttpRuntimeSettings() {
    };
    private static volatile HttpRuntimeSettings settings = DEFAULT_SETTINGS;
    private static final ThreadLocal<HttpRuntimeSettings> THREAD_OVERRIDE = new ThreadLocal<>();

    public static HttpRuntimeSettings get() {
        HttpRuntimeSettings override = THREAD_OVERRIDE.get();
        return override != null ? override : settings;
    }

    /**
     * Runs a synchronous action on the current thread with temporary settings, e.g. to test unsaved form values.
     * Work dispatched to other threads keeps seeing the global settings.
     */
    public static <T> T callWithThreadOverride(HttpRuntimeSettings overrideSettings, Supplier<T> action) {
        HttpRuntimeSettings previous = THREAD_OVERRIDE.get();
        THREAD_OVERRIDE.set(overrideSettings);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                THREAD_OVERRIDE.remove();
            } else {
                THREAD_OVERRIDE.set(previous);
            }
        }
    }

    public static void set(HttpRuntimeSettings newSettings) {
        settings = newSettings == null ? DEFAULT_SETTINGS : newSettings;
    }

    public static void reset() {
        settings = DEFAULT_SETTINGS;
    }
}
