package za.co.felixsol.util.foldersync;

import picocli.CommandLine;
import picocli.CommandLine.Option;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Callable;

public class FoldersyncApp implements Callable<Integer> {

    @Option(names = {"-s", "--source"}, description = "Source directory (repeatable)", required = true)
    private List<Path> sources = new ArrayList<>();

    @Option(names = {"-d", "--destination"}, description = "Destination directory (repeatable)", required = true)
    private List<Path> destinations = new ArrayList<>();

    @Option(names = {"-i", "--include"}, description = "Include pattern (glob)")
    private String includePattern;

    @Option(names = {"-e", "--exclude"}, description = "Exclude pattern (glob)")
    private String excludePattern;

    @Option(names = {"-w", "--watch"}, description = "Watch source for changes and sync continuously")
    private boolean watch = false;

    @Option(names = {"--force"}, description = "Force re-copy of files even if unchanged")
    private boolean force = false;

    @Option(names = {"--purge"}, description = "Purge (clear) destination contents before syncing; preserves foldersync.db")
    private boolean purge = false;

    @Option(names = {"--purge-db"}, description = "When used with --purge also remove the foldersync.db file")
    private boolean purgeDb = false;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new FoldersyncApp()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        var logger = org.slf4j.LoggerFactory.getLogger(FoldersyncApp.class);
        logger.info("Starting foldersync");

        // validate mapping rules
        if (destinations.size() != 1 && destinations.size() != sources.size()) {
            logger.error("Provide either one destination or the same number of destinations as sources.");
            return 2;
        }

        // build mapping: source -> list of destinations
        Map<Path, List<Path>> mapping = new LinkedHashMap<>();
        if (destinations.size() == 1) {
            Path onlyDest = destinations.get(0);
            for (Path s : sources) mapping.put(s, List.of(onlyDest));
        } else {
            for (int i = 0; i < sources.size(); i++) {
                mapping.put(sources.get(i), List.of(destinations.get(i)));
            }
        }

        // initial run for each mapping
            for (var entry : mapping.entrySet()) {
            Path src = entry.getKey();
                    for (Path dst : entry.getValue()) {
                if (purge) {
                    purgeDestination(dst);
                }
                Database db = Database.forDestination(dst);
                try {
                    Syncer syncer = new Syncer(db, includePattern, excludePattern, force);
                    syncer.sync(src, dst);
                } finally {
                    db.close();
                }
            }
        }

        if (watch) {
            logger.info("Entering watch mode; monitoring {} sources", mapping.size());
            watchAndSync(mapping);
        }

        logger.info("foldersync completed");
        return 0;
    }
    private void watchAndSync(Map<Path, List<Path>> mapping) throws Exception {
        // single WatchService monitoring all source trees; map watch keys back to their source root
        try (WatchService ws = FileSystems.getDefault().newWatchService()) {
            Map<WatchKey, Path> keyToSource = new HashMap<>();

            // register directories for each source root
            for (Path sourceRoot : mapping.keySet()) {
                Files.walk(sourceRoot)
                        .filter(Files::isDirectory)
                        .forEach(dir -> {
                            try {
                                WatchKey k = dir.register(ws, StandardWatchEventKinds.ENTRY_CREATE,
                                        StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
                                keyToSource.put(k, sourceRoot);
                            } catch (Exception e) {
                                // ignore registration failures
                            }
                        });
            }

            while (true) {
                WatchKey key;
                try {
                    key = ws.take();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }

                Path sourceRoot = keyToSource.get(key);
                boolean triggered = false;

                for (WatchEvent<?> ev : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = ev.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW) continue;
                    @SuppressWarnings("unchecked")
                    Path name = ((WatchEvent<Path>) ev).context();
                    Path child = null;
                    try {
                        if (sourceRoot != null) child = sourceRoot.resolve(name);
                    } catch (Exception ignored) {}

                    // if a new directory is created, register it under the same source root
                    if (kind == StandardWatchEventKinds.ENTRY_CREATE && child != null) {
                        try {
                            if (Files.isDirectory(child)) {
                                WatchKey k = child.register(ws, StandardWatchEventKinds.ENTRY_CREATE,
                                        StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
                                keyToSource.put(k, sourceRoot);
                            }
                        } catch (Exception ignore) {
                        }
                    }
                    triggered = true;
                }

                boolean valid = key.reset();
                if (!valid) keyToSource.remove(key);

                if (triggered && sourceRoot != null) {
                    // debounce
                    try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
                    // sync this source to its mapped destinations
                    List<Path> dests = mapping.get(sourceRoot);
                    if (dests != null) {
                        for (Path dst : dests) {
                            Database db = Database.forDestination(dst);
                            try {
                                Syncer syncer = new Syncer(db, includePattern, excludePattern, force);
                                syncer.sync(sourceRoot, dst);
                            } catch (Exception e) {
                                org.slf4j.LoggerFactory.getLogger(FoldersyncApp.class).warn("Watch sync failed for {} -> {}", sourceRoot, dst, e);
                            } finally {
                                db.close();
                            }
                        }
                    }
                }
            }
        }
    }

    private void purgeDestination(Path dst) {
        var logger = org.slf4j.LoggerFactory.getLogger(FoldersyncApp.class);
        try {
            if (!Files.exists(dst)) return;
            Path dbFile = dst.resolve("foldersync.db");
            // walk and delete children, preserve foldersync.db
            try (var stream = Files.walk(dst)) {
                stream.sorted(Comparator.reverseOrder())
                        .filter(p -> !p.equals(dst))
                        .forEach(p -> {
                            try {
                                if (p.equals(dbFile) && !purgeDb) return;
                                Files.deleteIfExists(p);
                            } catch (Exception e) {
                                logger.warn("Failed to delete {} during purge", p, e);
                            }
                        });
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(FoldersyncApp.class).warn("Purge failed for {}", dst, e);
        }
    }
}
