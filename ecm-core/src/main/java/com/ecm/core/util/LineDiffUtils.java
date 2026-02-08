package com.ecm.core.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A small, dependency-free, line-based diff utility.
 *
 * <p>This is designed for UI display and small files only. Callers must enforce limits
 * (bytes/lines/chars) to keep runtime and payload size bounded.</p>
 */
public final class LineDiffUtils {

    private LineDiffUtils() {}

    public record DiffOutput(String diff, boolean truncated) {}

    private enum OpType {
        EQUAL,
        DELETE,
        INSERT
    }

    private record Op(OpType type, String line) {}

    public static DiffOutput diff(String fromText, String toText, int maxLines, int maxChars) {
        String safeFrom = fromText == null ? "" : fromText;
        String safeTo = toText == null ? "" : toText;

        List<String> fromLines = new ArrayList<>();
        List<String> toLines = new ArrayList<>();
        boolean truncated = false;

        truncated |= readLines(safeFrom, maxLines, fromLines);
        truncated |= readLines(safeTo, maxLines, toLines);

        List<Op> ops = computeOps(fromLines, toLines);

        StringBuilder sb = new StringBuilder(Math.min(maxChars, 32_000));
        sb.append("--- from\n+++ to\n");

        for (Op op : ops) {
            String prefix = switch (op.type) {
                case EQUAL -> "  ";
                case DELETE -> "- ";
                case INSERT -> "+ ";
            };
            sb.append(prefix).append(op.line).append('\n');
            if (sb.length() > maxChars) {
                truncated = true;
                break;
            }
        }

        String diff = sb.toString();
        if (diff.length() > maxChars) {
            diff = diff.substring(0, maxChars);
        }
        return new DiffOutput(diff, truncated);
    }

    private static boolean readLines(String text, int maxLines, List<String> out) {
        if (maxLines <= 0) {
            return true;
        }
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (count >= maxLines) {
                    return true;
                }
                out.add(line);
                count += 1;
            }
        } catch (IOException e) {
            // StringReader shouldn't throw in practice; treat as truncated for safety.
            return true;
        }
        return false;
    }

    /**
     * Compute operations to transform {@code fromLines} into {@code toLines} using LCS.
     */
    private static List<Op> computeOps(List<String> fromLines, List<String> toLines) {
        int n = fromLines.size();
        int m = toLines.size();

        int[][] dp = new int[n + 1][m + 1];
        for (int i = 1; i <= n; i += 1) {
            String a = fromLines.get(i - 1);
            for (int j = 1; j <= m; j += 1) {
                String b = toLines.get(j - 1);
                if (Objects.equals(a, b)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }

        List<Op> ops = new ArrayList<>();
        int i = n;
        int j = m;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && Objects.equals(fromLines.get(i - 1), toLines.get(j - 1))) {
                ops.add(new Op(OpType.EQUAL, fromLines.get(i - 1)));
                i -= 1;
                j -= 1;
                continue;
            }

            if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
                ops.add(new Op(OpType.INSERT, toLines.get(j - 1)));
                j -= 1;
            } else if (i > 0) {
                ops.add(new Op(OpType.DELETE, fromLines.get(i - 1)));
                i -= 1;
            }
        }

        Collections.reverse(ops);
        return ops;
    }
}

