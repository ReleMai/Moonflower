package haven.multisession;

import haven.Coord;
import haven.HeadlessClient;
import haven.Utils;
import haven.Area;
import haven.render.FragColor;
import haven.render.NumberFormat;
import haven.render.Pipe;
import haven.render.Render;
import haven.render.VectorFormat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.ImageIO;

/** Bounded, asynchronous RGB readback from a headless Haven render loop. */
final class SessionWorkerFrameOutput implements HeadlessClient.FrameOutput {
    private static final long FRAME_INTERVAL_NANOS = 100_000_000L;

    private final Coord size;
    private final SessionWorkerProtocol.Writer writer;
    private final Runnable failure;
    private final BlockingQueue<RawFrame> pendingFrames = new ArrayBlockingQueue<>(1);
    private final AtomicBoolean readPending = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread encoder;
    private volatile long lastCapture;
    private long sequence;

    SessionWorkerFrameOutput(Coord size, SessionWorkerProtocol.Writer writer, Runnable failure) {
        this.size = size;
        this.writer = writer;
        this.failure = failure;
        encoder = new Thread(this::encodeLoop, "MoonFlower worker frame encoder");
        encoder.setDaemon(true);
        encoder.start();
    }

    @Override
    public void accept(Render g, Pipe state) {
        if(!running.get())
            return;
        long now = System.nanoTime();
        if((now - lastCapture) < FRAME_INTERVAL_NANOS ||
                !readPending.compareAndSet(false, true))
            return;
        lastCapture = now;
        ByteBuffer buffer = Utils.mkbbuf(size.x * size.y * 3);
        try {
            g.pget(state, FragColor.fragcol, Area.sized(size),
                    new VectorFormat(3, NumberFormat.UNORM8), buffer, data -> {
                        try {
                            byte[] rgb = new byte[size.x * size.y * 3];
                            for(int i = 0; i < rgb.length; i++)
                                rgb[i] = data.get(i);
                            pendingFrames.offer(new RawFrame(sequence++, rgb));
                        } finally {
                            readPending.set(false);
                        }
                    });
        } catch(RuntimeException e) {
            readPending.set(false);
            fail();
        }
    }

    private void encodeLoop() {
        try {
            while(running.get()) {
                RawFrame raw = pendingFrames.take();
                BufferedImage image = new BufferedImage(size.x, size.y, BufferedImage.TYPE_INT_RGB);
                for(int y = 0; y < size.y; y++) {
                    int sourceY = size.y - 1 - y;
                    int row = sourceY * size.x * 3;
                    for(int x = 0; x < size.x; x++) {
                        int at = row + x * 3;
                        int red = raw.rgb[at] & 0xff;
                        int green = raw.rgb[at + 1] & 0xff;
                        int blue = raw.rgb[at + 2] & 0xff;
                        image.setRGB(x, y, (red << 16) | (green << 8) | blue);
                    }
                }
                ByteArrayOutputStream encoded = new ByteArrayOutputStream(size.x * size.y / 2);
                if(!ImageIO.write(image, "jpg", encoded))
                    throw(new IOException("JPEG encoder is unavailable."));
                byte[] payload = SessionWorkerProtocol.frame(raw.sequence, size.x, size.y,
                        encoded.toByteArray());
                writer.send(SessionWorkerProtocol.FRAME, payload);
            }
        } catch(InterruptedException ignored) {
        } catch(IOException | RuntimeException e) {
            if(running.get())
                fail();
        }
    }

    private void fail() {
        if(running.getAndSet(false)) {
            pendingFrames.clear();
            encoder.interrupt();
            failure.run();
        }
    }

    @Override
    public void close() {
        running.set(false);
        pendingFrames.clear();
        encoder.interrupt();
    }

    private static final class RawFrame {
        private final long sequence;
        private final byte[] rgb;

        private RawFrame(long sequence, byte[] rgb) {
            this.sequence = sequence;
            this.rgb = rgb;
        }
    }
}
