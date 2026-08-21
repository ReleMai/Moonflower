# Client Data And Backup

## Authoritative Locations On Windows

| Data | Location | Notes |
| --- | --- | --- |
| Hurricane preferences | `%APPDATA%\Haven and Hearth\Hurricane-prefs.xml` | Window/UI/client settings; migrated from Java Preferences when first created |
| Optional user config | `%APPDATA%\Haven and Hearth\haven-config.properties` | Overrides packaged client properties when present |
| Map/resource cache | `%APPDATA%\Haven and Hearth\data\` | Hash-addressed storage; map records are stored under the configured map-cache identity |
| Legacy file cache | `%APPDATA%\Haven and Hearth\cache\` | Used when the older cache implementation is selected |
| Custom databases | `%APPDATA%\Haven and Hearth\Hurricane\` | `static_data.db`, `hitboxes.db`, and `saved_routes.db` |

`static_data.db`, `hitboxes.db`, and `saved_routes.db` used to be relative to the
client working directory. The revived client now resolves them through
`haven.ClientData`. If a packaged or legacy database exists and the AppData copy
does not, it is copied once before SQLite opens it. Existing AppData copies are
never overwritten by that migration.

## Backup Command

```powershell
.\scripts\backup-client-data.ps1
```

The default destination is `.recovery\client-data-<timestamp>`, which is ignored
by Git. The script copies the complete Haven AppData folder plus any legacy
database files found in `client\` or `client\bin\`.

To store the backup elsewhere:

```powershell
.\scripts\backup-client-data.ps1 -DestinationRoot "E:\Backups\Hurricane-before-update"
```

Stop all Haven client processes before making a final backup or restoring data.
Restore by copying the required files back only while the client is stopped.
