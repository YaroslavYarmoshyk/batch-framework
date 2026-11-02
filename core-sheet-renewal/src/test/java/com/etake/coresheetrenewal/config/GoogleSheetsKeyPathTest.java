package com.etake.coresheetrenewal.config;

import com.etake.googlesheets.ResourcePaths;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleSheetsKeyPathTest {

    @Test
    void aRelativePathMissingInTheWorkingDirectoryIsResolvedAgainstTheLibraryModule() {
        final File resolved = ResourcePaths.resolve("src/main/java/com/etake/googlesheets/ResourcePaths.java");

        assertThat(resolved).exists();
        assertThat(resolved.getPath()).contains("google-sheets-library");
    }
}
