package com.ecm.core.entity;

/**
 * Lock type following Alfresco semantics.
 * <ul>
 *   <li>{@link #WRITE_LOCK} — prevents other users from writing; owner can still write</li>
 *   <li>{@link #READ_ONLY_LOCK} — prevents ALL users (including owner) from writing</li>
 *   <li>{@link #NODE_LOCK} — prevents update/delete but allows adding children</li>
 * </ul>
 */
public enum LockType {
    WRITE_LOCK,
    READ_ONLY_LOCK,
    NODE_LOCK
}
