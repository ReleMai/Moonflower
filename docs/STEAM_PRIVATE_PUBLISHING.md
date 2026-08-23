# Private Steam Workshop Publishing

## Outcome

MoonFlower uses the official Haven & Hearth Steam Workshop under AppID
`3051280`. Its publishing path is locked to an owner-only `private` item. The
tracked metadata contains no Workshop item ID, no feature inventory, and no
repository link. A first upload therefore creates a new MoonFlower item rather
than updating the inherited Hurricane item.

Preparing a package is local and repeatable. Creating or updating a Workshop
item is a separate, visible, user-confirmed operation.

## What Private Does And Does Not Protect

Steam Workshop `private` visibility means only the creating Steam account can
see the item. It protects distribution through Steam, but it does not encrypt
the files installed on that account's computers.

No client-side Java protection can make executable behavior secret from a
person who can run the client. The JVM must eventually receive executable
bytecode and any local decryption key. A determined recipient can inspect the
loaded classes, memory, calls, and behavior.

The practical confidentiality layers are:

1. Keep the Workshop item owner-only/private.
2. Keep the source repository and publishing state private.
3. Package only an explicit allowlist of runtime files.
4. Never include AppData, preferences, map data, credentials, logs, or operator
   configuration.
5. Keep any future third-party source delivery limited to the recipients who
   are legally entitled to it.

## Encryption And Obfuscation Assessment

| Technique | Benefit | Limitation | MoonFlower decision |
| --- | --- | --- | --- |
| AES-encrypted nested JAR with a bundled key | Hides casual ZIP inspection | The client must contain or obtain the key; runtime extraction remains possible and custom loading increases breakage and security-review risk | Rejected |
| String encryption | Obscures selected constants | Reversible by definition; runtime values remain observable | Rejected as a secrecy claim |
| ProGuard name obfuscation | Removes names/debug metadata and raises the effort for casual decompilation | ProGuard is not a security tool; this reflection-heavy client needs extensive keep rules and runtime validation | Not enabled by default |
| JAR signing | Detects modification when signature verification is enforced | Provides integrity, not confidentiality | Possible future integrity work |
| Native compilation | Raises reverse-engineering cost | Still recoverable, complicates multi-platform Workshop delivery, licensing, native access, and updates | Not selected |
| Server-side proprietary logic | Keeps logic off recipient machines | Requires a deliberately secured service and changes availability, privacy, latency, and project architecture | Separate future design only |

Obfuscation may be evaluated later as a deterrent in a separate package, but it
must pass the complete packaged and supervised runtime gates and must never be
described as encryption or guaranteed secrecy. Keep obfuscation mappings out of
the Workshop package.

## License Boundary

`client/COPYING` states that `client/src/haven` and its subdirectories are
covered by GNU LGPL v3. MoonFlower's cookbook, fishing, feasting, combat, and
automation systems live in that tree. When a build is conveyed to another
person, the LGPL can require notices, license copies, corresponding source or
relinkable application material, and permission to modify the LGPL portions
and reverse-engineer them for debugging those modifications.

The private package includes `COPYING`, `GPL-3`, and `LGPL-3`. Owner-only use
does not make the executable cryptographically secret. Sharing the item with
friends changes the recipient boundary and requires a specific license review
and a private source-delivery plan. This document records the engineering
constraint; it is not legal advice.

## Prepare And Audit

Close every visible MoonFlower client first. The clean build guard refuses to
replace `client/bin/hafen.jar` while a client is running.

```powershell
.\scripts\prepare-private-steam-workshop.ps1
```

The script:

- performs a clean Ant package and the MoonFlower, Cookbook, Fishing,
  Feasting, Combat Assist, and resource checks;
- stages only allowlisted files under
  `.recovery\steam-workshop\package`;
- uses the tracked owner-only metadata;
- adds a valid MoonFlower Workshop ID only from ignored local state;
- excludes mutable and sensitive files;
- verifies that the JAR contains no operator bridge, map uploader, remote
  cookbook integration, update checker, or plaintext saved-account UI;
- rejects web/operator directories and literal IP addresses in staged text and
  configuration files;
- includes license notices; and
- writes `private-publish-manifest.json` with file sizes and SHA-256 hashes.

`-SkipBuild` and `-SkipChecks` exist only for script development and audits of
an existing package. Output prepared with either switch is not release proof.

Review at minimum:

```powershell
Get-Content .\.recovery\steam-workshop\package\workshop-client.properties
Get-Content .\.recovery\steam-workshop\package\private-publish-manifest.json
```

The metadata must say `visibility=private`. The first package must not contain
`workshop-id`. No package may contain the inherited Hurricane ID
`3423755273`.

## Publish Checkpoint

Publishing requires Steam to be visibly running under the intended owner
account. It also requires the exact manifest hash printed by preparation, the
explicit switch, and a typed confirmation phrase.

```powershell
.\scripts\publish-private-steam-workshop.ps1 `
    -ExpectedManifestSha256 "<reviewed hash>" `
    -ConfirmPrivateUpload
```

The Java uploader independently refuses:

- missing explicit upload confirmation;
- any item name other than MoonFlower;
- any visibility other than `private`;
- any AppID other than `3051280`; and
- the inherited Hurricane Workshop ID.

After a first item is created, its ID is written to the ignored local file
`.recovery\steam-workshop\workshop-id.txt`. A later preparation adds that ID
only to the staged metadata so updates target the same private MoonFlower item.

If an upload reports failure after returning a new ID, do not retry blindly.
Inspect the ignored ID and the Steam Workshop page first to avoid creating
duplicate items.

## Supervised Steam Verification

After an authorized upload:

1. Open the item page and verify the creator account, new MoonFlower item ID,
   and owner-only private visibility.
2. Back up mutable client data with `scripts/backup-client-data.ps1`.
3. Subscribe with the owner account and launch Haven & Hearth through Steam.
4. Confirm MoonFlower appears in Haven's Workshop client chooser.
5. Confirm the packaged client reaches the login screen.
6. Treat real-account login and world entry as separate supervised checks.

Keep the item private during all validation. The current publishing tools do
not support selective per-person access. Steam's `friends` mode would expose
the item to every Steam friend and is deliberately blocked by this private
publishing path.

## Account And Network Privacy

The private package does not include local Java preferences, AppData, map
caches, session files, logs, cookies, passwords, account names, client IP
records, or operator/web configuration. MoonFlower also removes the custom
plaintext `Save Account` feature and does not persist Haven login tokens.
Known legacy keys are deleted without displaying their values when the client
starts.

To remove legacy sensitive keys immediately while the client is closed, run:

```powershell
.\scripts\clear-local-sensitive-client-data.ps1
```

The cleanup uses Java's own Properties XML reader/writer, preserves nonsensitive
MoonFlower settings, and does not create a credential-bearing backup.

Credentials and Steam/Haven session material still exist briefly in process
memory while authenticating. The client must also connect to Haven's official
authentication, game, resource, status, and Steam services to function. This
privacy boundary removes optional third-party/operator web features; it cannot
make an online game client perform no network communication.

## Rollback

If ownership, AppID, item ID, metadata, or visibility is unexpected:

1. Stop before subscribing or launching.
2. Set the Workshop item to private in Steam if it is not already private.
3. Preserve the returned item ID and command output for diagnosis.
4. Do not reuse or modify item `3423755273`.
5. Re-run local preparation and compare the new manifest before another
   authorized upload.
