package com.example.foldersync;

import picocli.CommandLine;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

public class FoldersyncApp implements Callable<Integer> {

    @Option(names = {"-s", "--source"}, description = "Source directory", required = true)
    private Path source;

    @Option(names = {"-d", "--destination"}, description = "Destination directory", required = true)
    private Path destination;

    @Option(names = {"-i", "--include"}, description = "Include pattern (glob)")
    private String includePattern;

    @Option(names = {"-e", "--exclude"}, description = "Exclude pattern (glob)")
    private String excludePattern;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new FoldersyncApp()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        var logger = org.slf4j.LoggerFactory.getLogger(FoldersyncApp.class);
        logger.info("Starting foldersync from {} to {}", source, destination);

        Database db = Database.forDestination(destination);
        try {
            Syncer syncer = new Syncer(db, includePattern, excludePattern);
            syncer.sync(source, destination);
        } finally {
            db.close();
        }

        logger.info("foldersync completed");
        return 0;
    }
}
