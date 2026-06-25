package com.example.foldersync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.function.Predicate;

public class Syncer {
    private static final Logger LOG = LoggerFactory.getLogger(Syncer.class);

    private final Database db;
    private final String includePattern;
    private final String excludePattern;

    public Syncer(Database db, String includePattern, String excludePattern) {
        this.db = db;
        this.includePattern = includePattern;
        this.excludePattern = excludePattern;
    }

    public void sync(Path source, Path dest) throws IOException {
        if (!Files.isDirectory(source)) {
            throw new IllegalArgumentException("Source must be a directory");
        }
        Files.createDirectories(dest);

        Predicate<Path> include = p -> true;
        if (includePattern != null && !includePattern.isBlank()) {
            PathMatcher m = source.getFileSystem().getPathMatcher("glob:" + includePattern);
            include = p -> m.matches(p.getFileName());
        }

        Predicate<Path> exclude = p -> false;
        if (excludePattern != null && !excludePattern.isBlank()) {
            PathMatcher m = source.getFileSystem().getPathMatcher("glob:" + excludePattern);
            exclude = p -> m.matches(p.getFileName());
        }

        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                try {
                    Path rel = source.relativize(file);
                    if (!include.test(file) || exclude.test(file)) {
                        return FileVisitResult.CONTINUE;
                    }
                    Path target = dest.resolve(rel);
                    Files.createDirectories(target.getParent());

                    String checksum = sha256(file);
                    long lastModified = Files.getLastModifiedTime(file).toMillis();

                    boolean shouldCopy = true;
                    if (db.hasRecord(rel.toString())) {
                        shouldCopy = false; // placeholder: compare checksum in future
                    }

                    if (shouldCopy) {
                        LOG.info("Copying {} -> {}", file, target);
                        Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                        db.upsertRecord(rel.toString(), checksum, lastModified);
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
