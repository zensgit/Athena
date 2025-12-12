package com.ecm.core.sanity;

public interface SanityChecker {
    /**
     * Perform the sanity check.
     * @param fix Whether to attempt to fix found issues.
     * @return The report of the check.
     */
    SanityCheckReport check(boolean fix);

    String getName();
}
