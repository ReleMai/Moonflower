package haven.multisession;

/**
 * Non-sensitive identity and sizing metadata for a background session worker.
 * Authentication usernames, cookies, tokens, and passwords do not belong here.
 */
public final class SessionWorkerProfile {
    private final String workerId;
    private final String accountLabel;
    private final String preferredCharacter;
    private final String server;
    private final int previewWidth;
    private final int previewHeight;

    public SessionWorkerProfile(String workerId, String accountLabel, String preferredCharacter,
                                String server, int previewWidth, int previewHeight) {
        this.workerId = required(workerId, "worker id");
        this.accountLabel = required(accountLabel, "account label");
        this.preferredCharacter = optional(preferredCharacter);
        this.server = required(server, "server");
        if((previewWidth < 320) || (previewHeight < 180))
            throw(new IllegalArgumentException("Worker preview must be at least 320x180."));
        this.previewWidth = previewWidth;
        this.previewHeight = previewHeight;
    }

    public String workerId() {return(workerId);}
    public String accountLabel() {return(accountLabel);}
    public String preferredCharacter() {return(preferredCharacter);}
    public String server() {return(server);}
    public int previewWidth() {return(previewWidth);}
    public int previewHeight() {return(previewHeight);}

    private static String required(String value, String label) {
        String clean = optional(value);
        if(clean.isEmpty())
            throw(new IllegalArgumentException(label + " is required."));
        return(clean);
    }

    private static String optional(String value) {
        return((value == null) ? "" : value.trim());
    }
}
