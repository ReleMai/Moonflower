package haven.multisession;

import haven.Coord;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Small private protocol between the visible MoonFlower client and one
 * headless worker JVM. It is deliberately length-framed so a dropped worker
 * cannot leave the parent blocked halfway through a message.
 */
public final class SessionWorkerProtocol {
    public static final int MAGIC = 0x4d465331; // "MFS1"
    public static final int MAX_PAYLOAD = 16 * 1024 * 1024;
    public static final int NONCE_SIZE = 32;

    public static final byte HELLO = 1;
    public static final byte AUTH = 2;
    public static final byte STATUS = 3;
    public static final byte FRAME = 4;
    public static final byte INPUT = 5;
    public static final byte SHUTDOWN = 6;

    private SessionWorkerProtocol() {
    }

    public static final class Message {
        public final byte type;
        public final byte[] payload;

        private Message(byte type, byte[] payload) {
            this.type = type;
            this.payload = payload;
        }
    }

    /** Serializes writes from the worker's status and frame threads. */
    public static final class Writer {
        private final DataOutputStream out;

        public Writer(DataOutputStream out) {
            if(out == null)
                throw(new IllegalArgumentException("Protocol output is required."));
            this.out = out;
        }

        public synchronized void send(byte type, byte[] payload) throws IOException {
            write(out, type, payload);
        }
    }

    public static void write(DataOutputStream out, byte type, byte[] payload) throws IOException {
        byte[] safe = (payload == null) ? new byte[0] : payload;
        if(safe.length > MAX_PAYLOAD)
            throw(new IOException("Worker protocol payload is too large."));
        out.writeInt(MAGIC);
        out.writeByte(type);
        out.writeInt(safe.length);
        out.write(safe);
        out.flush();
    }

    public static Message read(DataInputStream in) throws IOException {
        int magic;
        try {
            magic = in.readInt();
        } catch(EOFException e) {
            throw(e);
        }
        if(magic != MAGIC)
            throw(new IOException("Invalid worker protocol header."));
        byte type = in.readByte();
        int length = in.readInt();
        if((length < 0) || (length > MAX_PAYLOAD))
            throw(new IOException("Invalid worker protocol payload length."));
        byte[] payload = new byte[length];
        in.readFully(payload);
        return(new Message(type, payload));
    }

    public static byte[] hello(byte[] nonce) {
        if(nonce == null || nonce.length != NONCE_SIZE)
            throw(new IllegalArgumentException("Worker nonce must be 32 bytes."));
        return(copy(nonce));
    }

    public static byte[] auth(byte[] nonce, String username, byte[] cookie) throws IOException {
        if(nonce == null || nonce.length != NONCE_SIZE)
            throw(new IllegalArgumentException("Worker nonce is required."));
        if(username == null || username.isBlank())
            throw(new IllegalArgumentException("Worker username is required."));
        if(cookie == null || cookie.length != 32)
            throw(new IllegalArgumentException("Worker cookie must be 32 bytes."));
        ByteArrayOutputStream raw = new ByteArrayOutputStream(128);
        DataOutputStream out = new DataOutputStream(raw);
        writeBytes(out, nonce, 64);
        writeString(out, username, 256);
        writeBytes(out, cookie, 32);
        out.flush();
        return(raw.toByteArray());
    }

    public static AuthPayload decodeAuth(byte[] payload) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        byte[] nonce = readBytes(in, 64);
        String username = readString(in, 256);
        byte[] cookie = readBytes(in, 32);
        if(in.available() != 0)
            throw(new IOException("Trailing worker authentication data."));
        return(new AuthPayload(nonce, username, cookie));
    }

    public static byte[] status(String status) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream(96);
        DataOutputStream out = new DataOutputStream(raw);
        writeString(out, status, 512);
        out.flush();
        return(raw.toByteArray());
    }

    public static String decodeStatus(byte[] payload) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        String status = readString(in, 512);
        if(in.available() != 0)
            throw(new IOException("Trailing worker status data."));
        return(status);
    }

    public static byte[] frame(long sequence, int width, int height, byte[] jpeg) throws IOException {
        if((width < 1) || (height < 1) || jpeg == null || jpeg.length == 0)
            throw(new IllegalArgumentException("A worker frame must contain an image."));
        ByteArrayOutputStream raw = new ByteArrayOutputStream(jpeg.length + 24);
        DataOutputStream out = new DataOutputStream(raw);
        out.writeLong(sequence);
        out.writeInt(width);
        out.writeInt(height);
        out.writeInt(jpeg.length);
        out.write(jpeg);
        out.flush();
        return(raw.toByteArray());
    }

    public static FramePayload decodeFrame(byte[] payload) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        long sequence = in.readLong();
        int width = in.readInt();
        int height = in.readInt();
        int length = in.readInt();
        if((width < 1) || (height < 1) || (length < 1) || (length > MAX_PAYLOAD) ||
                (length > in.available()))
            throw(new IOException("Invalid worker frame."));
        byte[] jpeg = new byte[length];
        in.readFully(jpeg);
        if(in.available() != 0)
            throw(new IOException("Trailing worker frame data."));
        return(new FramePayload(sequence, Coord.of(width, height), jpeg));
    }

    public enum InputType {
        MOUSE_DOWN(1), MOUSE_UP(2), MOUSE_MOVE(3), MOUSE_WHEEL(4), KEY_DOWN(5), KEY_UP(6);

        private final byte id;

        InputType(int id) {
            this.id = (byte)id;
        }

        static InputType fromId(byte id) throws IOException {
            for(InputType type : values()) {
                if(type.id == id)
                    return(type);
            }
            throw(new IOException("Unknown worker input type."));
        }
    }

    public static final class Input {
        public final InputType type;
        public final int x, y, button, wheel, keyCode, modifiers;
        public final char keyChar;

        private Input(InputType type, int x, int y, int button, int wheel,
                      int keyCode, int modifiers, char keyChar) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.button = button;
            this.wheel = wheel;
            this.keyCode = keyCode;
            this.modifiers = modifiers;
            this.keyChar = keyChar;
        }
    }

    /** Parent-side value object used to keep event construction readable. */
    public static final class InputBuilder {
        private final Input input;

        public InputBuilder(InputType type, int x, int y, int button, int wheel,
                            int keyCode, int modifiers, char keyChar) {
            input = new Input(type, x, y, button, wheel, keyCode, modifiers, keyChar);
        }

        Input input() {return(input);}
    }

    public static byte[] input(Input input) throws IOException {
        if(input == null || input.type == null)
            throw(new IllegalArgumentException("Worker input is required."));
        ByteArrayOutputStream raw = new ByteArrayOutputStream(32);
        DataOutputStream out = new DataOutputStream(raw);
        out.writeByte(input.type.id);
        out.writeInt(input.x);
        out.writeInt(input.y);
        out.writeInt(input.button);
        out.writeInt(input.wheel);
        out.writeInt(input.keyCode);
        out.writeInt(input.modifiers);
        out.writeChar(input.keyChar);
        out.flush();
        return(raw.toByteArray());
    }

    public static byte[] input(InputBuilder builder) throws IOException {
        return(input(builder == null ? null : builder.input()));
    }

    public static Input decodeInput(byte[] payload) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        InputType type = InputType.fromId(in.readByte());
        Input input = new Input(type, in.readInt(), in.readInt(), in.readInt(), in.readInt(),
                in.readInt(), in.readInt(), in.readChar());
        if(in.available() != 0)
            throw(new IOException("Trailing worker input data."));
        return(input);
    }

    private static void writeBytes(DataOutputStream out, byte[] value, int max) throws IOException {
        if(value == null || value.length > max)
            throw(new IOException("Worker protocol field is too large."));
        out.writeInt(value.length);
        out.write(value);
    }

    private static byte[] readBytes(DataInputStream in, int max) throws IOException {
        int length = in.readInt();
        if((length < 0) || (length > max) || (length > in.available()))
            throw(new IOException("Invalid worker protocol field."));
        byte[] value = new byte[length];
        in.readFully(value);
        return(value);
    }

    private static void writeString(DataOutputStream out, String value, int max) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeBytes(out, bytes, max);
    }

    private static String readString(DataInputStream in, int max) throws IOException {
        return(new String(readBytes(in, max), StandardCharsets.UTF_8));
    }

    private static byte[] copy(byte[] value) {
        return(value == null ? new byte[0] : value.clone());
    }

    public static final class AuthPayload {
        public final byte[] nonce;
        public final String username;
        public final byte[] cookie;

        private AuthPayload(byte[] nonce, String username, byte[] cookie) {
            this.nonce = nonce;
            this.username = username;
            this.cookie = cookie;
        }
    }

    public static final class FramePayload {
        public final long sequence;
        public final Coord size;
        public final byte[] jpeg;

        private FramePayload(long sequence, Coord size, byte[] jpeg) {
            this.sequence = sequence;
            this.size = size;
            this.jpeg = jpeg;
        }
    }
}
