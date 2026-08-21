package haven.botcontrol;

import haven.GameUI;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class ScreenshotCaptureService {
    private static final int MAX_DIMENSION = 1280;
    private static final float JPEG_QUALITY = 0.72f;
    private static final long CAPTURE_TIMEOUT_MS = 4000L;

    private final GameUI gui;

    public ScreenshotCaptureService(GameUI gui) {
        this.gui = gui;
    }

    public JSONObject capture() throws IOException {
        BufferedImage image = captureUiFrame();
        BufferedImage prepared = prepare(image);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        writeJpeg(prepared, outputStream);
        String screenshotId = UUID.randomUUID().toString();
        JSONObject metadata = new JSONObject();
        metadata.put("screenshotId", screenshotId);
        metadata.put("fileName", screenshotId + ".jpg");
        metadata.put("mediaType", "image/jpeg");
        metadata.put("width", prepared.getWidth());
        metadata.put("height", prepared.getHeight());
        metadata.put("createdAt", Instant.now().toString());
        metadata.put("captureSource", "ui-render");
        JSONObject payload = new JSONObject();
        payload.put("metadata", metadata);
        payload.put("base64Content", Base64.getEncoder().encodeToString(outputStream.toByteArray()));
        return payload;
    }

    private BufferedImage captureUiFrame() throws IOException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<BufferedImage> imageRef = new AtomicReference<>();
        gui.ui.drawafter(g -> g.getimage(img -> {
            imageRef.set(img);
            latch.countDown();
        }));
        try {
            if (!latch.await(CAPTURE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw new IOException("Timed out waiting for the game client to render a live frame.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for the game client to render a live frame.", e);
        }
        BufferedImage image = imageRef.get();
        if (image == null) {
            throw new IOException("The game client did not produce a frame for capture.");
        }
        return image;
    }

    private BufferedImage prepare(BufferedImage source) {
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        int longestEdge = Math.max(sourceWidth, sourceHeight);
        if (longestEdge <= MAX_DIMENSION) {
            BufferedImage converted = new BufferedImage(sourceWidth, sourceHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = converted.createGraphics();
            try {
                graphics.drawImage(source, 0, 0, null);
            } finally {
                graphics.dispose();
            }
            return converted;
        }

        double scale = MAX_DIMENSION / (double) longestEdge;
        int scaledWidth = Math.max(1, (int) Math.round(sourceWidth * scale));
        int scaledHeight = Math.max(1, (int) Math.round(sourceHeight * scale));
        BufferedImage scaled = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, scaledWidth, scaledHeight, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }

    private void writeJpeg(BufferedImage image, ByteArrayOutputStream outputStream) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("JPEG writer not available.");
        }
        ImageWriter writer = writers.next();
        try (MemoryCacheImageOutputStream imageOutput = new MemoryCacheImageOutputStream(outputStream)) {
            writer.setOutput(imageOutput);
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(JPEG_QUALITY);
            }
            writer.write(null, new javax.imageio.IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
    }
}
