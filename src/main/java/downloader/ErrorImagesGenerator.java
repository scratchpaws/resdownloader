package downloader;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

public class ErrorImagesGenerator {

    public void generateImageFromText(String text, Path outputFile) throws IOException  {

        BufferedImage bufImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D graph = bufImage.createGraphics();
        Font genFont = new Font("Default", Font.PLAIN, 24);
        graph.setFont(genFont);
        FontMetrics genFontMetric = graph.getFontMetrics();
        int imageWidth = genFontMetric.stringWidth(text);
        int imageHeight = genFontMetric.getHeight();
        graph.dispose();

        bufImage = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
        graph = bufImage.createGraphics();

        graph.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graph.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        graph.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
        graph.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        graph.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graph.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graph.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        graph.setFont(genFont);

        genFontMetric = graph.getFontMetrics();
        graph.setColor(Color.WHITE);
        graph.fillRect(0, 0, bufImage.getWidth(), bufImage.getHeight());
        graph.setColor(Color.RED);
        graph.drawString(text, 0, genFontMetric.getAscent());
        graph.dispose();

        ImageIO.write(bufImage, "png", outputFile.toFile());
    }
}
