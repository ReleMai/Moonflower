package haven.multisession;

/** Presentation-safe account launcher; it contains a label, never a secret. */
public final class SessionLaunchOptionSnapshot {
    private final String accountLabel;
    private final boolean directSignInReady;

    public SessionLaunchOptionSnapshot(String accountLabel, boolean directSignInReady) {
        if(accountLabel == null || accountLabel.isBlank())
            throw(new IllegalArgumentException("Account label is required."));
        this.accountLabel = accountLabel.trim();
        this.directSignInReady = directSignInReady;
    }

    public String accountLabel() {return(accountLabel);}
    public boolean directSignInReady() {return(directSignInReady);}
}
