# foldersync

This repository implements a one-way folder synchronization tool. See the project design and requirements in [copilot instructions](.github/copilot-instructions.md).

Build with `mvn package` and run the shaded jar produced in `target/`.

## Command-line usage

Build the project and run the shaded jar produced in `target/`:

```
mvn -B clean package
java -jar target/foldersync-0.1.0-shaded.jar -s /path/to/source -d /path/to/destination [options]
```

Example (Windows):

```
java -jar target/foldersync-0.1.0-shaded.jar -s "C:\Users\me\Documents\source" -d "D:\backups\dest" -i "*.txt" -e "temp*"
```

Options:

- `-s, --source`: Source directory (required).
- `-d, --destination`: Destination directory (required).
- `-i, --include`: Include glob pattern (optional). Example: `*.txt`.
- `-e, --exclude`: Exclude glob pattern (optional). Example: `temp*`.
 - `-w, --watch`: Watch the source directory for changes and sync continuously (optional).
 - `-w, --watch`: Watch the source directory for changes and sync continuously (optional).
 - Multiple `-s/--source` and `-d/--destination` options are supported. Repeat the options to provide multiple paths.
 - `--force`: Force re-copy of files even if unchanged (optional).
 - `--force`: Force re-copy of files even if unchanged (optional).
 - `--purge`: Clear destination contents before syncing. `foldersync.db` is preserved.
 - `--purge`: Clear destination contents before syncing. `foldersync.db` is preserved unless `--purge-db` is specified.
 - `--purge-db`: When used with `--purge`, also delete the `foldersync.db` file in the destination.

Mapping rules:

- If one destination and N sources → sync each source into that single destination.
- If N sources and N destinations → sync pairwise: source[i] -> destination[i].
- Otherwise the program exits with an error.

Examples:

- Multiple sources into one destination:

```
java -jar target/foldersync-0.1.0-shaded.jar -s "C:\src\one" -s "C:\src\two" -d "D:\backup"
```

Run as a Windows service (sc)

You can register the application as a Windows service using `sc create`. Be careful with quoting — the `binPath` should contain the full path to `java.exe` and the `-jar` command, wrapped in escaped quotes. Example (run from an Administrator PowerShell/CMD):

```
sc create FolderSyncService binPath= "\"C:\Program Files\Java\jdk-17\bin\java.exe\" -jar \"C:\git-work\foldersync\target\foldersync-0.1.0-shaded.jar\" -s \"C:\path\to\source\" -d \"D:\path\to\dest\" --watch" DisplayName= "FolderSync Service" start= auto
```

Then control the service with:

```
sc start FolderSyncService
sc stop FolderSyncService
sc delete FolderSyncService
```

Notes:
- Running as a Windows service will run the process in a non-interactive session — logs go to whatever logging backend you configured (logback). Consider configuring a file appender in `logback.xml`.
- If you need smoother service management (restarts, stdout/stderr capture, environment vars), consider using NSSM (Non-Sucking Service Manager) or a Windows service wrapper instead of `sc`.
- If the service must run under a specific account, add `obj= "DOMAIN\User" password= "p@ss"` to the `sc create` command (requires elevated privileges).


- Pairwise mapping:

```
java -jar target/foldersync-0.1.0-shaded.jar -s "C:\src\one" -d "D:\dest\one" -s "C:\src\two" -d "D:\dest\two"
```

Notes:

- A SQLite database file `foldersync.db` is created inside the destination directory to track files already copied.
- This tool performs one-way synchronization (source → destination) and does not delete files from the destination.
- If a file is being written to while copying is attempted, the file is skipped and a warning is logged; it will be retried on the next run.

Watch mode example:

```
java -jar target/foldersync-0.1.0-shaded.jar -s "C:\Users\me\Documents\source" -d "D:\backups\dest" -w
```


