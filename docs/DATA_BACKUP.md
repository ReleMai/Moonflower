# Client Data And Backup

## Authoritative Locations On Windows

| Data | Location | Notes |
| --- | --- | --- |
| MoonFlower preferences | `%APPDATA%\Haven and Hearth\MoonFlower-prefs.xml` | Window/UI/client settings |
| Optional user config | `%APPDATA%\Haven and Hearth\haven-config.properties` | Overrides packaged client properties when present |
| Map/resource cache | `%APPDATA%\Haven and Hearth\data\` | Hash-addressed storage; map records are stored under the configured map-cache identity |
| Legacy file cache | `%APPDATA%\Haven and Hearth\cache\` | Used when the older cache implementation is selected |
| Custom databases | `%APPDATA%\Haven and Hearth\MoonFlower\` | Includes `static_data.db`, `hitboxes.db`, `saved_routes.db`, `cookbook.db`, and `fishing.db` |

Legacy custom databases used to be relative to the client working directory.
The revived client now resolves custom data through `haven.ClientData`.
`cookbook.db` and `fishing.db` are client-local observation stores created in
AppData; they are not packaged seed databases. If a packaged or legacy database
exists and the AppData copy does not, it is copied once before SQLite opens it.
Existing AppData copies are never overwritten by that migration.

## Backup Command

```powershell
.\scripts\backup-client-data.ps1
```

The default destination is `.recovery\client-data-<timestamp>`, which is ignored
by Git. The script copies the complete Haven AppData folder plus any legacy
database files found in `client\` or `client\bin\`.

To store the backup elsewhere:

```powershell
.\scripts\backup-client-data.ps1 -DestinationRoot "E:\Backups\MoonFlower-before-update"
```

Stop all Haven client processes before making a final backup or restoring data.
Restore by copying the required files back only while the client is stopped.
