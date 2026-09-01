package haven.multisession;

import haven.Coord;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Owns one worker process, its framed preview stream, and its input pipe. */
final class SessionWorkerProcess {
    enum State { STARTING, AUTHENTICATING, CONNECTING, READY, FAILED, STOPPED }

    private static final long STALE_AFTER_MILLIS = 5_000L;
    private final SessionWorkerProfile profile;
    private final SessionWorkerLaunchSpec launch;
    private final Runnable changed;
    private final CountDownLatch hello = new CountDownLatch(1);
    private final Object outputLock = new Object();
    private volatile State state = State.STARTING;
    private volatile String detail = "Starting isolated worker.";
    private volatile BufferedImage latestFrame;
    private volatile long latestFrameAt;
    private volatile long latestSequence = -1;
    private volatile Process process;
    private volatile DataOutputStream control;
    private volatile byte[] workerNonce;
    private volatile boolean stopping;

    SessionWorkerProcess(SessionWorkerProfile profile, SessionWorkerLaunchSpec launch,
                         Runnable changed) {
        if(profile == null || launch == null)
            throw(new IllegalArgumentException("Worker profile and launch are required."));
        this.profile = profile;
        this.launch = launch;
        this.changed = (changed == null) ? () -> {} : changed;
    }

    void start(SessionWorkerAuthTicket ticket) throws Exception {
        if(ticket == null)
            throw(new IllegalArgumentException("Worker authentication ticket is required."));
        if(process != null)
            throw(new IllegalStateException("Worker has already been started."));
        Process child = null;
        try {
            child = new ProcessBuilder(command()).redirectErrorStream(false).start();
            process = child;
            control = new DataOutputStream(child.getOutputStream());
            final Process startedChild = child;
            Thread outputReader = new Thread(() -> readOutput(startedChild),
                    "MoonFlower worker output " + profile.workerId());
            outputReader.setDaemon(true);
            outputReader.start();
            Thread errorReader = new Thread(() -> drain(startedChild),
                    "MoonFlower worker diagnostics " + profile.workerId());
            errorReader.setDaemon(true);
            errorReader.start();
            if(!hello.await(15, TimeUnit.SECONDS))
                throw(new IOException("Worker handshake timed out."));
            if(state == State.FAILED)
                throw(new IOException("Worker handshake failed."));
            byte[] nonce = workerNonce;
            if(nonce == null)
                throw(new IOException("Worker handshake was incomplete."));
            setState(State.AUTHENTICATING, "Passing one-shot authentication to worker.");
            byte[] payload = null;
            try {
                payload = SessionWorkerProtocol.auth(nonce, ticket.username(), ticket.cookie());
                send(SessionWorkerProtocol.AUTH, payload);
            } finally {
                if(payload != null)
                    Arrays.fill(payload, (byte)0);
                ticket.clear();
                Arrays.fill(nonce, (byte)0);
                workerNonce = null;
            }
        } catch(Exception e) {
            ticket.clear();
            if(child != null)
                stop();
            setState(State.FAILED, "Worker could not be started.");
            throw(e);
        }
    }

    private List<String> command() {
        String javaHome = System.getProperty("java.home", "");
        Path executable = Paths.get(javaHome, "bin", "javaw.exe");
        if(!Files.isRegularFile(executable))
            executable = Paths.get(javaHome, "bin", "java.exe");
        String separator = java.io.File.pathSeparator;
        String java = Files.isRegularFile(executable) ? executable.toString() : "java";
        String classpath = System.getProperty("java.class.path", "");
        if(classpath.isBlank())
            throw(new IllegalStateException("Worker classpath is unavailable."));
        Path working = Paths.get("").toAbsolutePath().normalize();
        classpath = classpath + separator + working.resolve("*") + separator +
                working.resolve("bin").resolve("*") + separator +
                working.resolve("client").resolve("bin").resolve("*");
        ArrayList<String> command = new ArrayList<>();
        command.add(java);
        command.add("-Xss8m");
        command.add("--add-exports");
        command.add("java.base/java.lang=ALL-UNNAMED");
        command.add("--add-exports");
        command.add("java.desktop/sun.awt=ALL-UNNAMED");
        command.add("--add-exports");
        command.add("java.desktop/sun.java2d=ALL-UNNAMED");
        if(Runtime.version().feature() >= 22) {
            command.add("--enable-native-access=ALL-UNNAMED");
        }
        command.add("-cp");
        command.add(classpath);
        command.add(launch.mainClass());
        command.addAll(launch.arguments());
        return(command);
    }

    private void readOutput(Process child) {
        try(DataInputStream input = new DataInputStream(child.getInputStream())) {
            while(true) {
                SessionWorkerProtocol.Message message = SessionWorkerProtocol.read(input);
                handle(message);
            }
        } catch(IOException e) {
            if(!stopping) {
                setState(State.FAILED, "Worker connection closed.");
                terminate(child);
            }
        } finally {
            hello.countDown();
        }
    }

    private void drain(Process child) {
        try(java.io.InputStream input = child.getErrorStream()) {
            byte[] buffer = new byte[1024];
            while(input.read(buffer) >= 0) {
                /* Diagnostics are intentionally discarded. They can contain
                 * third-party exception text and must not enter MoonFlower
                 * status, logs, or IPC output. */
            }
        } catch(IOException ignored) {
        }
    }

    private void handle(SessionWorkerProtocol.Message message) throws IOException {
        if(message.type == SessionWorkerProtocol.HELLO) {
            if(message.payload.length != SessionWorkerProtocol.NONCE_SIZE || workerNonce != null)
                throw(new IOException("Invalid worker handshake."));
            workerNonce = message.payload.clone();
            hello.countDown();
        } else if(message.type == SessionWorkerProtocol.STATUS) {
            String status = SessionWorkerProtocol.decodeStatus(message.payload);
            if(status.equals("CONNECTING"))
                setState(State.CONNECTING, "Worker is connecting to the game server.");
            else if(status.equals("READY"))
                setState(State.READY, "Embedded worker viewport is ready.");
            else if(status.equals("FAILED"))
                setState(State.FAILED, "Worker reported a startup failure.");
        } else if(message.type == SessionWorkerProtocol.FRAME) {
            SessionWorkerProtocol.FramePayload frame =
                    SessionWorkerProtocol.decodeFrame(message.payload);
            if(!frame.size.equals(Coord.of(profile.previewWidth(), profile.previewHeight())))
                throw(new IOException("Worker frame size changed unexpectedly."));
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(frame.jpeg));
            if(image == null || image.getWidth() != frame.size.x || image.getHeight() != frame.size.y)
                throw(new IOException("Worker sent an invalid preview frame."));
            if(frame.sequence > latestSequence) {
                latestSequence = frame.sequence;
                latestFrame = image;
                latestFrameAt = System.currentTimeMillis();
            }
        }
    }

    private void send(byte type, byte[] payload) throws IOException {
        synchronized(outputLock) {
            if(control == null)
                throw(new IOException("Worker control channel is unavailable."));
            SessionWorkerProtocol.write(control, type, payload);
        }
    }

    void sendMouseDown(int x, int y, int button, int modifiers) {
        sendInput(new SessionWorkerProtocol.InputBuilder(
                SessionWorkerProtocol.InputType.MOUSE_DOWN, x, y, button, 0, 0, modifiers, (char)0));
    }

    void sendMouseUp(int x, int y, int button, int modifiers) {
        sendInput(new SessionWorkerProtocol.InputBuilder(
                SessionWorkerProtocol.InputType.MOUSE_UP, x, y, button, 0, 0, modifiers, (char)0));
    }

    void sendMouseMove(int x, int y, int modifiers) {
        sendInput(new SessionWorkerProtocol.InputBuilder(
                SessionWorkerProtocol.InputType.MOUSE_MOVE, x, y, 0, 0, 0, modifiers, (char)0));
    }

    void sendMouseWheel(int x, int y, int amount, int modifiers) {
        sendInput(new SessionWorkerProtocol.InputBuilder(
                SessionWorkerProtocol.InputType.MOUSE_WHEEL, x, y, 0, amount, 0, modifiers, (char)0));
    }

    void sendKey(boolean down, int keyCode, int modifiers, char keyChar) {
        sendInput(new SessionWorkerProtocol.InputBuilder(
                down ? SessionWorkerProtocol.InputType.KEY_DOWN : SessionWorkerProtocol.InputType.KEY_UP,
                0, 0, 0, 0, keyCode, modifiers, keyChar));
    }

    private void sendInput(SessionWorkerProtocol.InputBuilder builder) {
        if(state != State.READY || stopping)
            return;
        try {
            send(SessionWorkerProtocol.INPUT, SessionWorkerProtocol.input(builder.input()));
        } catch(IOException e) {
            if(!stopping) {
                setState(State.FAILED, "Worker input channel closed.");
                terminate(process);
            }
        }
    }

    private void setState(State value, String valueDetail) {
        state = value;
        detail = (valueDetail == null || valueDetail.isBlank()) ? value.toString() : valueDetail;
        notifyChanged();
    }

    private void notifyChanged() {
        try {
            changed.run();
        } catch(RuntimeException ignored) {
        }
    }

    private static void terminate(Process child) {
        if(child == null || !child.isAlive())
            return;
        child.destroy();
        try {
            if(!child.waitFor(1, TimeUnit.SECONDS))
                child.destroyForcibly();
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            child.destroyForcibly();
        }
    }

    void stop() {
        stopping = true;
        Process child = process;
        try {
            if(child != null && child.isAlive()) {
                try {
                    send(SessionWorkerProtocol.SHUTDOWN, new byte[0]);
                } catch(IOException ignored) {
                }
                try {
                    child.getOutputStream().close();
                } catch(IOException ignored) {
                }
                if(!child.waitFor(2, TimeUnit.SECONDS)) {
                    child.destroy();
                    if(!child.waitFor(2, TimeUnit.SECONDS))
                        child.destroyForcibly();
                }
            }
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            if(child != null)
                child.destroy();
        } finally {
            state = State.STOPPED;
            detail = "Worker stopped.";
            notifyChanged();
        }
    }

    SessionCardSnapshot snapshot(boolean selected) {
        State current = state;
        SessionSurfaceState surface;
        if(current == State.STARTING || current == State.AUTHENTICATING || current == State.CONNECTING)
            surface = SessionSurfaceState.STARTING;
        else if(current == State.READY && ((latestFrame == null) ||
                (System.currentTimeMillis() - latestFrameAt > STALE_AFTER_MILLIS)))
            surface = SessionSurfaceState.STALE;
        else if(current == State.READY)
            surface = SessionSurfaceState.READY;
        else
            surface = SessionSurfaceState.DISCONNECTED;
        return(new SessionCardSnapshot(profile.workerId(), profile.accountLabel(),
                profile.preferredCharacter(), profile.server(), surface, selected,
                current != State.STOPPED, java.time.Instant.now(), detail));
    }

    BufferedImage latestFrame() {return(latestFrame);}
    Coord previewSize() {return(Coord.of(profile.previewWidth(), profile.previewHeight()));}
    String workerId() {return(profile.workerId());}
    String accountLabel() {return(profile.accountLabel());}
    State state() {return(state);}
}
