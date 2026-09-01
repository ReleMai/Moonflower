package haven.multisession;

import haven.Bootstrap;
import haven.Client;
import haven.Connection;
import haven.Coord;
import haven.HeadlessClient;
import haven.NamedSocketAddress;
import haven.RemoteUI;
import haven.UI;
import haven.Utils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Entry point for one isolated, no-native-window Haven session. */
public final class SessionWorkerMain {
    private SessionWorkerMain() {
    }

    public static void main(String[] args) {
        PrintStream protocol = System.out;
        System.setOut(new PrintStream(OutputStream.nullOutputStream(), true));
        try {
            Options options = Options.parse(args);
            run(options, protocol);
        } catch(Throwable ignored) {
            /* Parent receives a generic failure status; stdout remains a
             * protocol stream and must never contain a stack trace. */
            try {
                new SessionWorkerProtocol.Writer(new DataOutputStream(protocol))
                        .send(SessionWorkerProtocol.STATUS,
                                SessionWorkerProtocol.status("FAILED"));
            } catch(Throwable ignoredAgain) {
            }
        }
    }

    private static void run(Options options, PrintStream protocol) throws Exception {
        DataInputStream control = new DataInputStream(System.in);
        SessionWorkerProtocol.Writer writer =
                new SessionWorkerProtocol.Writer(new DataOutputStream(protocol));
        byte[] nonce = new byte[32];
        new SecureRandom().nextBytes(nonce);
        writer.send(SessionWorkerProtocol.HELLO, SessionWorkerProtocol.hello(nonce));

        SessionWorkerProtocol.Message message = SessionWorkerProtocol.read(control);
        if(message.type != SessionWorkerProtocol.AUTH)
            throw(new IOException("Worker authentication was not provided."));
        SessionWorkerProtocol.AuthPayload auth = null;
        try {
            auth = SessionWorkerProtocol.decodeAuth(message.payload);
            if(!Arrays.equals(nonce, auth.nonce))
                throw(new IOException("Worker authentication nonce mismatch."));
        } catch(Exception e) {
            if(auth != null) {
                Arrays.fill(auth.nonce, (byte)0);
                Arrays.fill(auth.cookie, (byte)0);
            }
            Arrays.fill(message.payload, (byte)0);
            Arrays.fill(nonce, (byte)0);
            throw(e);
        }
        Arrays.fill(message.payload, (byte)0);
        Arrays.fill(nonce, (byte)0);

        Thread workerThread = Thread.currentThread();
        SessionWorkerInputDispatcher input = null;
        SessionWorkerFrameOutput frames = null;
        try {
            writer.send(SessionWorkerProtocol.STATUS,
                    SessionWorkerProtocol.status("CONNECTING"));
            Utils.initlocale();
            Bootstrap.authserv.set(options.authServer);
            Bootstrap.gameserv.set(options.gameServer);
            Bootstrap.authuser.set(auth.username);
            Bootstrap.authck.set(auth.cookie);
            Connection.encrypt.set(options.encrypted);
            Client.setupres();
            UI.Runner remote = new RemoteUI(Client.connect(new String[0]));

            /* Client.connect consumes the cookie while constructing the
             * session. Clear every worker-side copy before the UI starts. */
            Bootstrap.authuser.set(null);
            Bootstrap.authck.set(null);
            Arrays.fill(auth.cookie, (byte)0);

            input = new SessionWorkerInputDispatcher(control, workerThread::interrupt);
            frames = new SessionWorkerFrameOutput(options.size, writer, workerThread::interrupt);
            writer.send(SessionWorkerProtocol.STATUS,
                    SessionWorkerProtocol.status("READY"));
            PrintWriter quiet = new PrintWriter(OutputStream.nullOutputStream());
            new HeadlessClient(options.size, frames, input, quiet).run(remote, false);
        } finally {
            if(input != null)
                input.close();
            if(frames != null)
                frames.close();
            Bootstrap.authuser.set(null);
            Bootstrap.authck.set(null);
            Arrays.fill(auth.nonce, (byte)0);
            Arrays.fill(auth.cookie, (byte)0);
        }
    }

    private static final class Options {
        private final Coord size;
        private final NamedSocketAddress authServer;
        private final NamedSocketAddress gameServer;
        private final boolean encrypted;

        private Options(Coord size, NamedSocketAddress authServer,
                        NamedSocketAddress gameServer, boolean encrypted) {
            this.size = size;
            this.authServer = authServer;
            this.gameServer = gameServer;
            this.encrypted = encrypted;
        }

        private static Options parse(String[] args) {
            Map<String, String> values = new HashMap<>();
            Set<String> allowed = Set.of("--size", "--prefs", "--auth-server",
                    "--game-server", "--encrypt");
            for(int i = 0; i < args.length; i++) {
                String name = args[i];
                if(!allowed.contains(name) || (i + 1 >= args.length) || values.containsKey(name))
                    throw(new IllegalArgumentException("Invalid worker option."));
                values.put(name, args[++i]);
            }
            Coord size = parseSize(values.get("--size"));
            String prefs = required(values, "--prefs");
            Utils.prefspec.set(prefs);
            NamedSocketAddress auth = NamedSocketAddress.parse(
                    values.getOrDefault("--auth-server", "localhost:" + haven.AuthClient.DEFPORT),
                    haven.AuthClient.DEFPORT);
            NamedSocketAddress game = NamedSocketAddress.parse(required(values, "--game-server"), 0);
            String encrypt = values.getOrDefault("--encrypt", "false");
            if(!encrypt.equals("true") && !encrypt.equals("false"))
                throw(new IllegalArgumentException("Invalid worker encryption option."));
            boolean encrypted = Boolean.parseBoolean(encrypt);
            return(new Options(size, auth, game, encrypted));
        }

        private static Coord parseSize(String value) {
            if(value == null)
                throw(new IllegalArgumentException("Worker size is required."));
            int split = value.indexOf('x');
            if(split <= 0 || split >= value.length() - 1)
                throw(new IllegalArgumentException("Invalid worker size."));
            int width = Integer.parseInt(value.substring(0, split));
            int height = Integer.parseInt(value.substring(split + 1));
            if(width < 320 || height < 180 || width > 3840 || height > 2160)
                throw(new IllegalArgumentException("Worker size is outside safe bounds."));
            return(Coord.of(width, height));
        }

        private static String required(Map<String, String> values, String key) {
            String value = values.get(key);
            if(value == null || value.isBlank())
                throw(new IllegalArgumentException("Worker option is required."));
            return(value);
        }
    }
}
