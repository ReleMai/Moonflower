# Upstream Provenance

## Repositories

| Role | URL | Verified ref on 2026-08-21 |
| --- | --- | --- |
| Custom-client upstream | `https://github.com/Nightdawg/Hurricane.git` | `v1.69` / `045b1f598a9009b279b778eeb2256c651baf88f8` |
| Official vanilla client | `git://sh.seatribe.se/hafen-client` | `f4b86b85514ef3d77d95b08be0bea46179ef0b3a` |

Hurricane `v1.69` states that it merges Loftar's latest code. This should be
rechecked on every future update rather than assumed from this document.

## Local Base Reconstruction

The workspace arrived without Git metadata. The local client was compared
against Hurricane tags using file hashes. `v1.59b`
(`ef9fe0dd8`) was the unique closest match:

| Tag | Exact checked files | Different | Local files absent at tag |
| --- | ---: | ---: | ---: |
| `v1.59a` | 797 | 25 | 10 |
| `v1.59b` | 816 | 7 | 9 |
| `v1.60` | 794 | 29 | 9 |
| `v1.69` | 704 | 109 | 19 |

The seven differences and nine local-only Java files are listed in
`docs/CURRENT_STATE_AUDIT.md`. This makes `v1.59b` the merge base for the local
custom patch.

## Update Policy

1. Fetch, do not guess, the latest stable Hurricane tag and official HEAD.
2. Record exact hashes before changes.
3. Compare official HEAD with Hurricane's incorporated official merge.
4. Generate/review the local patch against the reconstructed base.
5. Apply that patch to the new Hurricane target and resolve each overlapping
   client seam deliberately.
6. Exclude upstream `Release/` binaries from this source workspace; build local
   deliverables from source.
7. Run the resource update checker after the Java port.
8. Record the target and verification results here.

## Current Port

- Base: Hurricane `v1.59b` (`ef9fe0dd8`)
- Target: Hurricane `v1.69` (`045b1f598a9009b279b778eeb2256c651baf88f8`)
- Upstream range: 533 commits and 982 changed files
- Local port commit: `a1bfde0`
- Status: source port complete; builds, fetched-resource check, and local
  platform smoke test pass. Real-game login remains supervised and unverified.
