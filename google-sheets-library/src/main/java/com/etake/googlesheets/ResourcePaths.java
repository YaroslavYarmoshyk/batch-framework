package com.etake.googlesheets;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;

/**
 * Resolves the configured path of the service account key so it works regardless of the JVM's working directory.
 *
 * <p>A configured path is used verbatim when absolute. When relative it is resolved against, in order: (1) the current
 * working directory if the file exists there, then (2) this library's module directory, where the key shared by all
 * Google Sheets jobs lives ({@code google/service-account-key.json}, git-ignored). A packaged application has no module
 * directory and should configure an absolute path. The key must stay out of {@code src/main/resources}, which would
 * bundle it into the jar.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ResourcePaths {

    public static File resolve(final String configured) {
        final File asConfigured = new File(configured);
        if (asConfigured.isAbsolute()) {
            return asConfigured;
        }

        final File workingDirBased = asConfigured.getAbsoluteFile();
        if (workingDirBased.exists()) {
            return workingDirBased;
        }

        final File moduleRoot = moduleRoot();
        return moduleRoot != null ? new File(moduleRoot, configured) : workingDirBased;
    }

    private static File moduleRoot() {
        try {
            final CodeSource codeSource = ResourcePaths.class.getProtectionDomain().getCodeSource();
            if (codeSource == null) {
                return null;
            }
            final URL location = codeSource.getLocation();
            final File codeLocation = new File(location.toURI());
            File dir = codeLocation.isFile() ? codeLocation.getParentFile() : codeLocation;
            while (dir != null) {
                if (new File(dir, "build.gradle").isFile()) {
                    return dir;
                }
                dir = dir.getParentFile();
            }
        } catch (final URISyntaxException | RuntimeException e) {
            log.debug("Could not determine module directory from code location", e);
        }
        return null;
    }
}
