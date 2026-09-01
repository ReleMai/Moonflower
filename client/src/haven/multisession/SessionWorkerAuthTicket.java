package haven.multisession;

import java.util.Arrays;

/** One-use in-memory authentication result passed to a worker process. */
final class SessionWorkerAuthTicket {
    private final String username;
    private byte[] cookie;

    SessionWorkerAuthTicket(String username, byte[] cookie) {
        if(username == null || username.isBlank())
            throw(new IllegalArgumentException("Worker username is required."));
        if(cookie == null || cookie.length != 32)
            throw(new IllegalArgumentException("Worker cookie must be 32 bytes."));
        this.username = username;
        this.cookie = cookie;
    }

    String username() {return(username);}

    byte[] cookie() {
        if(cookie == null)
            throw(new IllegalStateException("Worker ticket has already been consumed."));
        return(cookie);
    }

    void clear() {
        if(cookie != null) {
            Arrays.fill(cookie, (byte)0);
            cookie = null;
        }
    }
}
