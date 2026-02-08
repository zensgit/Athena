package com.ecm.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LineDiffUtilsTest {

    @Test
    @DisplayName("Diff output includes inserted and deleted lines")
    void diffIncludesInsertDeletePrefixes() {
        String from = "alpha\nbeta\n";
        String to = "alpha\ngamma\n";

        LineDiffUtils.DiffOutput out = LineDiffUtils.diff(from, to, 100, 10_000);

        assertTrue(out.diff().contains("--- from"));
        assertTrue(out.diff().contains("+++ to"));
        assertTrue(out.diff().contains("- beta"));
        assertTrue(out.diff().contains("+ gamma"));
    }

    @Test
    @DisplayName("Diff truncates when maxLines is exceeded")
    void diffTruncatesWhenMaxLinesExceeded() {
        String from = "a\nb\nc\n";
        String to = "a\nb\nc\nd\n";

        LineDiffUtils.DiffOutput out = LineDiffUtils.diff(from, to, 1, 10_000);

        assertTrue(out.truncated());
    }
}

