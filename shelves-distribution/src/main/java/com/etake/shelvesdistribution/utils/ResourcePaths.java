package com.etake.shelvesdistribution.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;

/**
 * Resolves the configured resource paths (see {@code system-configurations.output}) so they work
 * regardless of the JVM's working directory.
 *
 * <p>A configured path is used verbatim when absolute. When relative it is resolved against, in
 * order: (1) the current working directory if the file already exists there, then (2) the module
 * directory — the nearest ancestor of the running classes/jar that contains a {@code build.gradle}.
 * If neither yields an existing file (e.g. an output directory that is about to be created) the
 * module-relative location is preferred, falling back to the working-directory location when the
 * module cannot be determined.
 */
@Slf4j
public final class ResourcePaths {

    private ResourcePaths() {
    }

    /**
     * Resolve a configured path to an absolute {@link File}. Never returns {@code null}.
     */
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
        if (moduleRoot != null) {
            return new File(moduleRoot, configured);
        }
        return workingDirBased;
    }

    /**
     * The module directory — the nearest ancestor of the running code (classes directory in an IDE /
     * bootRun, or the jar file when packaged) that contains a {@code build.gradle}. Returns
     * {@code null} when it cannot be determined.
     */
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
