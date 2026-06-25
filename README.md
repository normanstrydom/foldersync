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


