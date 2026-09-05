package com.laker.postman.common.component;

import lombok.experimental.UtilityClass;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.HeadlessException;
import java.awt.Window;

/**
 * Checks whether a top-level window can use uniform opacity on the current display device.
 */
@UtilityClass
public class WindowOpacitySupport {

    public static boolean isOpacitySupported(Window window) {
        if (window == null) {
            return false;
        }
        return isOpacitySupported(window.getGraphicsConfiguration());
    }

    static boolean isOpacitySupported(GraphicsConfiguration graphicsConfiguration) {
        if (graphicsConfiguration == null) {
            return false;
        }
        try {
            GraphicsDevice device = graphicsConfiguration.getDevice();
            return device != null
                    && device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT);
        } catch (HeadlessException | SecurityException ignored) {
            return false;
        }
    }
}
