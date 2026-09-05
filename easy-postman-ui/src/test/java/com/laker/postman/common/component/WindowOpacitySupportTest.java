package com.laker.postman.common.component;

import org.testng.annotations.Test;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.geom.AffineTransform;
import java.awt.image.ColorModel;
import java.awt.Rectangle;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class WindowOpacitySupportTest {

    @Test
    public void shouldRejectMissingGraphicsConfiguration() {
        assertFalse(WindowOpacitySupport.isOpacitySupported((GraphicsConfiguration) null));
    }

    @Test
    public void shouldUseGraphicsDeviceOpacityCapability() {
        assertTrue(WindowOpacitySupport.isOpacitySupported(graphicsConfiguration(true)));
        assertFalse(WindowOpacitySupport.isOpacitySupported(graphicsConfiguration(false)));
    }

    private static GraphicsConfiguration graphicsConfiguration(boolean opacitySupported) {
        GraphicsDevice device = new GraphicsDevice() {
            @Override
            public int getType() {
                return TYPE_RASTER_SCREEN;
            }

            @Override
            public String getIDstring() {
                return "test";
            }

            @Override
            public GraphicsConfiguration[] getConfigurations() {
                return new GraphicsConfiguration[0];
            }

            @Override
            public GraphicsConfiguration getDefaultConfiguration() {
                return null;
            }

            @Override
            public boolean isWindowTranslucencySupported(WindowTranslucency translucencyKind) {
                return opacitySupported && translucencyKind == WindowTranslucency.TRANSLUCENT;
            }
        };
        return new GraphicsConfiguration() {
            @Override
            public GraphicsDevice getDevice() {
                return device;
            }

            @Override
            public ColorModel getColorModel() {
                return ColorModel.getRGBdefault();
            }

            @Override
            public ColorModel getColorModel(int transparency) {
                return ColorModel.getRGBdefault();
            }

            @Override
            public AffineTransform getDefaultTransform() {
                return new AffineTransform();
            }

            @Override
            public AffineTransform getNormalizingTransform() {
                return new AffineTransform();
            }

            @Override
            public Rectangle getBounds() {
                return new Rectangle(0, 0, 100, 100);
            }
        };
    }
}
