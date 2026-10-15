package com.laker.postman.panel.collections.tree;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import org.testng.annotations.Test;

import javax.swing.JPanel;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class CollectionImportIconResourcesTest {
    private static final List<String> IMPORT_ICONS = List.of(
            "easy.svg",
            "postman.svg",
            "swagger.svg",
            "openapi.svg",
            "insomnia.svg",
            "idea-http.svg",
            "apipost.svg",
            "curl.svg"
    );

    @Test
    public void collectionImportIconsShouldBeLocalColorSvgs() throws IOException {
        for (String iconName : IMPORT_ICONS) {
            String svg = Files.readString(Path.of("src/main/resources/icons", iconName));

            assertTrue(svg.contains("<svg"), iconName + " should be an SVG resource");
            assertTrue(svg.contains("viewBox="), iconName + " should provide a scalable viewBox");
            assertFalse(svg.contains("currentColor"), iconName + " should keep its source color instead of theme tinting");
            assertTrue(svg.contains("#"), iconName + " should keep source colors");
        }
    }

    @Test
    public void collectionImportIconsShouldKeepExpectedSourceVectors() throws IOException {
        assertIconContains("postman.svg", "viewBox=\"0 0 115 115\"", "#FF6C37", "M63.7963 0.464959");
        assertIconContains("swagger.svg", "viewBox=\"0 0 101 101\"", "#85EA2D", "M31.802,33.854");
        assertIconContains("openapi.svg", "viewBox=\"34 118 132 128\"", "#93D500", "M153.276,128.706");
        assertIconContains("insomnia.svg", "viewBox=\"-96 -96 960 960\"", "#5849be", "M316.487,141.518");
        assertIconContains("idea-http.svg", "viewBox=\"-7.5 -7.5 75 75\"", "#07C3F2", "M0 0v22.6326");
        assertIconContains("apipost.svg", "viewBox=\"-11.75 -11.75 117.5 117.5\"", "#FF780C", "M64.599 62.959");
        assertIconContains("curl.svg", "viewBox=\"0 0 24 24\"", "#093754", "#0f8a70", "d=\"m7 9 3 3-3 3\"");
    }

    @Test
    public void collectionImportIconsShouldRenderWithFlatSvgIcon() {
        JPanel host = new JPanel();
        for (String iconName : IMPORT_ICONS) {
            FlatSVGIcon icon = new FlatSVGIcon("icons/" + iconName, 20, 20);
            BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                icon.paintIcon(host, graphics, 0, 0);
            } finally {
                graphics.dispose();
            }
            assertTrue(hasVisiblePixels(image), iconName + " should render visible pixels at menu size");
        }
    }

    private static boolean hasVisiblePixels(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (((image.getRGB(x, y) >>> 24) & 0xff) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void assertIconContains(String iconName, String... expectedFragments) throws IOException {
        String svg = Files.readString(Path.of("src/main/resources/icons", iconName));
        for (String expectedFragment : expectedFragments) {
            assertTrue(svg.contains(expectedFragment), iconName + " should keep source fragment: " + expectedFragment);
        }
    }
}
