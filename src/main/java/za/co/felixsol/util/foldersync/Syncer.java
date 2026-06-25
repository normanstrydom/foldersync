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
import java.util.function.Predicate;

public class Syncer {
    private static final Logger LOG = LoggerFactory.getLogger(Syncer.class);

    private final Database db;
    private final String includePattern;
    private final String excludePattern;
    private final boolean force;

    public Syncer(Database db, String includePattern, String excludePattern, boolean force) {
        this.db = db;
        this.includePattern = includePattern;
        this.excludePattern = excludePattern;
        this.force = force;
    }

    public void sync(Path source, Path dest) throws IOException {
        if (!Files.isDirectory(source)) {
            throw new IllegalArgumentException("Source must be a directory");
        }
        Files.createDirectories(dest);

        final PathMatcher includeMatcher;
        if (includePattern != null && !includePattern.isBlank()) {
            includeMatcher = source.getFileSystem().getPathMatcher("glob:" + includePattern);
        } else {
            includeMatcher = null;
        }

        final PathMatcher excludeMatcher;
        if (excludePattern != null && !excludePattern.isBlank()) {
            excludeMatcher = source.getFileSystem().getPathMatcher("glob:" + excludePattern);
        } else {
            excludeMatcher = null;
        }

        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                try {
                    Path rel = source.relativize(file);
                    if ((includeMatcher != null && !includeMatcher.matches(file.getFileName()))
                            || (excludeMatcher != null && excludeMatcher.matches(file.getFileName()))) {
                        return FileVisitResult.CONTINUE;
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
