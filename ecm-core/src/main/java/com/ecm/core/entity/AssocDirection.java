package com.ecm.core.entity;

/**
 * Direction/kind of a node association.
 * <ul>
 *   <li>{@link #PEER} — symmetric peer association (e.g. "references", "related-to")</li>
 *   <li>{@link #CHILD_PRIMARY} — primary parent→child (filesystem hierarchy)</li>
 *   <li>{@link #CHILD_SECONDARY} — secondary parent→child (multi-filing)</li>
 * </ul>
 */
public enum AssocDirection {
    PEER,
    CHILD_PRIMARY,
    CHILD_SECONDARY
}
