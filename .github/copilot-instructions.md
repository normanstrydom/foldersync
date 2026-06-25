---
description: This file describes the design requirements and features of the project.
---

# Copilot Instructions for foldersync

## Design Requirements
- The project should be implemented in Java using Maven build tool.
- The project should be able to synchronize files and folders between two directories.
- Options to include or exclude specific file types or patterns should be provided.
- Options to include or exclude specific subdirectories should be provided.
- The synchronization should be one-way (from source to destination) and should not delete files in the destination that are not present in the source.
- The synchronization should be able to keep track of all files copied and prevent duplicate copies even if the destination file was removed.  
- Where a duplicate file is detected, the project should only copy the file if it has been modified. (Even if the file has been removed from the destination, it should be copied again if it has been modified in the source.)
- The synchronization should be able to handle large files and directories efficiently.
- Files in the source directory must not be locked in any way that could prevent them from being modified or deleted while the synchronization is in progress.
- The project should provide a command-line interface (CLI) for users to specify the source and destination directories, as well as any optional parameters (e.g., file filters, logging options).
- Files in the source directory be tested to ensure they are not being written to or modified during the synchronization process. If a file is being modified, the synchronization should skip that file and log a warning message and continue with the next file.  The skipped file should be retried in the next synchronization run.
- The project should provide logging functionality to track the synchronization process, including any errors or warnings encountered.
- The project should be able to handle errors gracefully and provide meaningful error messages to the user.
- All logs and tracking of copied files should be stored in a sqlite database in the destination directory. The database should be created if it does not exist and should be updated with each synchronization run.

## Build Environment Requirements

### Local development environment
- Java Development Kit (JDK) 17 or higher - F:\java\openlogic-openjdk-17.0.18+8-windows-x64
- Maven 3.8.6 or higher - F:\java\apache-maven-3.9.13

### Remote build environment
- Github Actions runner with Java 17 and Maven 3.8.6 or higher installed.
- Release build should be triggered by a push to the main branch and should create a release artifact in the target directory.

Important - the project's main README.md file should include a link to this instructions file for reference and must be kept up to date with any changes to the instructions and requirements.  Detailed information about the project, including usage instructions, should be included in the README.md file.

