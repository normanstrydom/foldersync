package za.co.felixsol.util.foldersync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Syncer {
    private static final Logger LOG = LoggerFactory.getLogger(Syncer.class);

    private final Database db;
    private final List<String> includePatterns;
    private final List<String> excludePatterns;
    private final boolean force;

    public Syncer(Database db, List<String> includePatterns, List<String> excludePatterns, boolean force) {
        this.db = db;
        this.includePatterns = includePatterns == null ? List.of() : includePatterns;
        this.excludePatterns = excludePatterns == null ? List.of() : excludePatterns;
        this.force = force;
    }

    public void sync(Path source, Path dest) throws IOException {
        if (!Files.isDirectory(source)) {
            throw new IllegalArgumentException("Source must be a directory");
        }
        Files.createDirectories(dest);

        final List<PathMatcher> includeMatchers = new ArrayList<>();
        for (String p : includePatterns) {
            if (p != null && !p.isBlank()) includeMatchers.add(source.getFileSystem().getPathMatcher("glob:" + p));
        }

        final List<PathMatcher> excludeMatchers = new ArrayList<>();
        for (String p : excludePatterns) {
            if (p != null && !p.isBlank()) excludeMatchers.add(source.getFileSystem().getPathMatcher("glob:" + p));
        }

        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                try {
                    Path rel = source.relativize(file);

                    // include: if include matchers provided, require at least one to match
                    if (!includeMatchers.isEmpty()) {
                        boolean any = false;
                        for (PathMatcher m : includeMatchers) {
                            if (m.matches(rel)) { any = true; break; }
                        }
                        if (!any) return FileVisitResult.CONTINUE;
                    }

                    // exclude: if any exclude matcher matches, skip the file
                    if (!excludeMatchers.isEmpty()) {
                        boolean anyEx = false;
                        for (PathMatcher m : excludeMatchers) {
                            if (m.matches(rel)) { anyEx = true; break; }
                        }
                        if (anyEx) return FileVisitResult.CONTINUE;
                    }
                    Path target = dest.resolve(rel);
                    Files.createDirectories(target.getParent());

                    String checksum = sha256(file);
                    long lastModified = Files.getLastModifiedTime(file).toMillis();
                    String lastModifiedText = Instant.ofEpochMilli(lastModified)
                            .atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

                    boolean shouldCopy = true;
                    Database.FileRecord existing = db.getRecord(rel.toString());
                    if (existing != null) {
                        boolean checksumChanged = (existing.checksum == null && checksum != null)
                                || (existing.checksum != null && !existing.checksum.equals(checksum));
                        boolean timeChanged = existing.lastModified != lastModified;
                        shouldCopy = checksumChanged || timeChanged;
                    }

                    if (force) {
                        shouldCopy = true;
                    }

                    if (shouldCopy) {
                        LOG.info("Copying {} -> {}", file, target);
                        Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                        db.upsertRecord(rel.toString(), checksum, lastModified, lastModifiedText);
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to copy file {}", file, e);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static String sha256(Path file) {
        try (var in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) md.update(buf, 0, r);
            byte[] d = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
