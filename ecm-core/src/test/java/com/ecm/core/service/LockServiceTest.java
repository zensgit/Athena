package com.ecm.core.service;

import com.ecm.core.entity.*;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.repository.NodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LockServiceTest {

    @Mock private NodeRepository nodeRepository;
    @Mock private SecurityService securityService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private TenantWorkspaceScopeService tenantWorkspaceScopeService;

    private LockService lockService;

    @BeforeEach
    void setUp() {
        lockService = new LockService(nodeRepository, securityService, eventPublisher, tenantWorkspaceScopeService);
    }

    // ===================================================================== lock types

    @Nested
    @DisplayName("lock types")
    class LockTypes {

        @Test
        @DisplayName("WRITE_LOCK is the default lock type")
        void defaultLockTypeIsWriteLock() {
            Folder folder = folder("workspace");
            stubLockable(folder);

            lockService.lock(folder.getId(), null);

            assertEquals(LockType.WRITE_LOCK, folder.getLockType());
        }

        @Test
        @DisplayName("READ_ONLY_LOCK persists to entity")
        void readOnlyLockPersists() {
            Folder folder = folder("workspace");
            stubLockable(folder);

            lockService.lock(folder.getId(), LockType.READ_ONLY_LOCK);

            assertEquals(LockType.READ_ONLY_LOCK, folder.getLockType());
            assertTrue(folder.isLocked());
            verify(nodeRepository).save(folder);
        }

        @Test
        @DisplayName("NODE_LOCK persists to entity")
        void nodeLockPersists() {
            Folder folder = folder("workspace");
            stubLockable(folder);

            lockService.lock(folder.getId(), LockType.NODE_LOCK);

            assertEquals(LockType.NODE_LOCK, folder.getLockType());
        }
    }

    @Test
    @DisplayName("scoped tenant cannot lock hidden node")
    void hiddenTenantNodeLooksMissing() {
        Folder folder = folder("workspace");
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(folder.getId(), Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.of(folder));
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(true);
        when(tenantWorkspaceScopeService.isPathVisible(folder.getPath())).thenReturn(false);

        assertThrows(NoSuchElementException.class, () -> lockService.lock(folder.getId(), LockType.WRITE_LOCK));
    }

    // ===================================================================== lifetime & expiry

    @Nested
    @DisplayName("lifetime & expiry")
    class LifetimeExpiry {

        @Test
        @DisplayName("EPHEMERAL lock with seconds sets expiresAt")
        void ephemeralLockSetsExpiry() {
            Folder folder = folder("workspace");
            stubLockable(folder);

            lockService.lock(folder.getId(), LockType.WRITE_LOCK, 120);

            assertEquals(LockLifetime.EPHEMERAL, folder.getLockLifetime());
            assertNotNull(folder.getLockExpiresAt());
        }

        @Test
        @DisplayName("PERSISTENT lock has no expiry")
        void persistentLockHasNoExpiry() {
            Folder folder = folder("workspace");
            stubLockable(folder);

            lockService.lock(folder.getId(), LockType.WRITE_LOCK);

            assertEquals(LockLifetime.PERSISTENT, folder.getLockLifetime());
            assertNull(folder.getLockExpiresAt());
        }

        @Test
        @DisplayName("rejects zero or negative duration")
        void rejectsNegativeDuration() {
            Folder folder = folder("workspace");
            when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(folder.getId(), Node.ArchiveStatus.LIVE))
                .thenReturn(Optional.of(folder));
            when(securityService.hasPermission(folder, PermissionType.WRITE)).thenReturn(true);

            assertThrows(IllegalArgumentException.class,
                () -> lockService.lock(folder.getId(), LockType.WRITE_LOCK, 0));
        }
    }

    // ===================================================================== additional info

    @Nested
    @DisplayName("additional info")
    class AdditionalInfo {

        @Test
        @DisplayName("stores additional info metadata with lock")
        void storesAdditionalInfo() {
            Folder folder = folder("workspace");
            stubLockable(folder);

            lockService.lock(folder.getId(), LockType.WRITE_LOCK, LockLifetime.PERSISTENT,
                null, false, "Editing in Collabora");

            assertEquals("Editing in Collabora", folder.getLockAdditionalInfo());
        }

        @Test
        @DisplayName("getAdditionalInfo returns stored metadata")
        void getsAdditionalInfo() {
            Folder folder = folder("workspace");
            folder.setLockAdditionalInfo("Editing in Collabora");
            when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(folder.getId(), Node.ArchiveStatus.LIVE))
                .thenReturn(Optional.of(folder));

            assertEquals("Editing in Collabora", lockService.getAdditionalInfo(folder.getId()));
        }
    }

    // ===================================================================== deep lock

    @Nested
    @DisplayName("deep (recursive) locking")
    class DeepLock {

        @Test
        @DisplayName("lockChildren=true locks parent and children recursively")
        void deepLockLocksChildren() {
            Folder parent = folder("workspace");
            Folder child1 = folder("sub1");
            child1.setParent(parent);
            parent.getChildren().add(child1);

            when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(parent.getId(), Node.ArchiveStatus.LIVE))
                .thenReturn(Optional.of(parent));
            when(securityService.hasPermission(parent, PermissionType.WRITE)).thenReturn(true);
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(nodeRepository.save(any(Node.class))).thenAnswer(inv -> inv.getArgument(0));

            lockService.lock(parent.getId(), LockType.WRITE_LOCK, LockLifetime.PERSISTENT,
                null, true, null);

            assertTrue(parent.isLocked());
            assertTrue(parent.isLockDeep());
            assertTrue(child1.isLocked());
            verify(nodeRepository, atLeast(2)).save(any(Node.class));
        }

        @Test
        @DisplayName("lockChildren=false does not lock children")
        void shallowLockIgnoresChildren() {
            Folder parent = folder("workspace");
            Folder child1 = folder("sub1");
            child1.setParent(parent);
            parent.getChildren().add(child1);

            stubLockable(parent);

            lockService.lock(parent.getId(), LockType.WRITE_LOCK, LockLifetime.PERSISTENT,
                null, false, null);

            assertTrue(parent.isLocked());
            assertFalse(child1.isLocked());
        }
    }

    // ===================================================================== unlock

    @Nested
    @DisplayName("unlock")
    class Unlock {

        @Test
        @DisplayName("owner can unlock")
        void ownerCanUnlock() {
            Folder folder = folder("workspace");
            folder.applyLock("alice", LocalDateTime.now(), LockLifetime.PERSISTENT, null);
            when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(folder.getId(), Node.ArchiveStatus.LIVE))
                .thenReturn(Optional.of(folder));
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(nodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            lockService.unlock(folder.getId());

            assertFalse(folder.isLocked());
        }

        @Test
        @DisplayName("admin can unlock others' locks")
        void adminCanUnlockOthers() {
            Folder folder = folder("workspace");
            folder.applyLock("bob", LocalDateTime.now(), LockLifetime.PERSISTENT, null);
            when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(folder.getId(), Node.ArchiveStatus.LIVE))
                .thenReturn(Optional.of(folder));
            when(securityService.getCurrentUser()).thenReturn("admin");
            when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
            when(nodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            lockService.unlock(folder.getId());

            assertFalse(folder.isLocked());
        }

        @Test
        @DisplayName("non-owner non-admin cannot unlock")
        void nonOwnerCannotUnlock() {
            Folder folder = folder("workspace");
            folder.applyLock("bob", LocalDateTime.now(), LockLifetime.PERSISTENT, null);
            when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(folder.getId(), Node.ArchiveStatus.LIVE))
                .thenReturn(Optional.of(folder));
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);

            assertThrows(SecurityException.class, () -> lockService.unlock(folder.getId()));
        }

        @Test
        @DisplayName("deep unlock clears children locks")
        void deepUnlockClearsChildren() {
            Folder parent = folder("workspace");
            Folder child = folder("sub");
            child.setParent(parent);
            parent.getChildren().add(child);
            parent.applyLock("alice", LocalDateTime.now(), LockLifetime.PERSISTENT, null);
            child.applyLock("alice", LocalDateTime.now(), LockLifetime.PERSISTENT, null);

            when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(parent.getId(), Node.ArchiveStatus.LIVE))
                .thenReturn(Optional.of(parent));
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);
            when(nodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            lockService.unlock(parent.getId(), true);

            assertFalse(parent.isLocked());
            assertFalse(child.isLocked());
        }
    }

    // ===================================================================== batch

    @Nested
    @DisplayName("batch operations")
    class BatchOps {

        @Test
        @DisplayName("batchLock locks multiple nodes")
        void batchLockLocksMultiple() {
            Folder f1 = folder("f1");
            Folder f2 = folder("f2");
            when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(f1.getId(), Node.ArchiveStatus.LIVE)).thenReturn(Optional.of(f1));
            when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(f2.getId(), Node.ArchiveStatus.LIVE)).thenReturn(Optional.of(f2));
            when(securityService.hasPermission(any(Node.class), eq(PermissionType.WRITE))).thenReturn(true);
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(nodeRepository.save(any(Node.class))).thenAnswer(inv -> inv.getArgument(0));

            lockService.batchLock(List.of(f1.getId(), f2.getId()), LockType.WRITE_LOCK, 600);

            assertTrue(f1.isLocked());
            assertTrue(f2.isLocked());
        }

        @Test
        @DisplayName("batchUnlock unlocks multiple nodes")
        void batchUnlockUnlocksMultiple() {
            Folder f1 = folder("f1");
            Folder f2 = folder("f2");
            f1.applyLock("alice", LocalDateTime.now(), LockLifetime.PERSISTENT, null);
            f2.applyLock("alice", LocalDateTime.now(), LockLifetime.PERSISTENT, null);
            when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(f1.getId(), Node.ArchiveStatus.LIVE)).thenReturn(Optional.of(f1));
            when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(f2.getId(), Node.ArchiveStatus.LIVE)).thenReturn(Optional.of(f2));
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(nodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            lockService.batchUnlock(List.of(f1.getId(), f2.getId()));

            assertFalse(f1.isLocked());
            assertFalse(f2.isLocked());
        }
    }

    // ===================================================================== status queries

    @Nested
    @DisplayName("status queries")
    class StatusQueries {

        @Test
        @DisplayName("isLocked returns true for actively locked node")
        void isLockedReturnsTrueForLocked() {
            Folder folder = folder("workspace");
            folder.applyLock("alice", LocalDateTime.now(), LockLifetime.PERSISTENT, null);
            when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(folder.getId(), Node.ArchiveStatus.LIVE))
                .thenReturn(Optional.of(folder));

            assertTrue(lockService.isLocked(folder.getId()));
        }

        @Test
        @DisplayName("isLockedAndReadOnly returns true for READ_ONLY_LOCK")
        void isLockedAndReadOnlyForReadOnlyLock() {
            Folder folder = folder("workspace");
            folder.applyLock("alice", LocalDateTime.now(), LockLifetime.PERSISTENT, null,
                LockType.READ_ONLY_LOCK, null, false);
            when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(folder.getId(), Node.ArchiveStatus.LIVE))
                .thenReturn(Optional.of(folder));

            assertTrue(lockService.isLockedAndReadOnly(folder.getId()));
        }

        @Test
        @DisplayName("isLockedAndReadOnly returns false for WRITE_LOCK")
        void isLockedAndReadOnlyFalseForWriteLock() {
            Folder folder = folder("workspace");
            folder.applyLock("alice", LocalDateTime.now(), LockLifetime.PERSISTENT, null,
                LockType.WRITE_LOCK, null, false);
            when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(folder.getId(), Node.ArchiveStatus.LIVE))
                .thenReturn(Optional.of(folder));

            assertFalse(lockService.isLockedAndReadOnly(folder.getId()));
        }

        @Test
        @DisplayName("checkForLock throws when locked by other user")
        void checkForLockThrowsWhenLockedByOther() {
            Folder folder = folder("workspace");
            folder.applyLock("bob", LocalDateTime.now(), LockLifetime.PERSISTENT, null);
            when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(folder.getId(), Node.ArchiveStatus.LIVE))
                .thenReturn(Optional.of(folder));
            when(securityService.getCurrentUser()).thenReturn("alice");

            assertThrows(SecurityException.class, () -> lockService.checkForLock(folder.getId()));
        }

        @Test
        @DisplayName("checkForLock passes when lock is suspended")
        void checkForLockPassesWhenSuspended() {
            Folder folder = folder("workspace");
            folder.applyLock("bob", LocalDateTime.now(), LockLifetime.PERSISTENT, null);

            lockService.suspendLocks();
            try {
                assertDoesNotThrow(() -> lockService.checkForLock(folder.getId()));
            } finally {
                lockService.enableLocks();
            }
        }
    }

    // ===================================================================== suspension

    @Nested
    @DisplayName("lock suspension")
    class Suspension {

        @Test
        @DisplayName("suspendLocks / enableLocks toggles thread-local flag")
        void suspendAndEnable() {
            assertFalse(lockService.areLocksSuspended());
            lockService.suspendLocks();
            assertTrue(lockService.areLocksSuspended());
            lockService.enableLocks();
            assertFalse(lockService.areLocksSuspended());
        }
    }

    // ===================================================================== write semantics

    @Nested
    @DisplayName("Node.isWriteAllowed semantics")
    class WriteSemantics {

        @Test
        @DisplayName("WRITE_LOCK allows owner to write")
        void writeLockAllowsOwner() {
            Folder folder = folder("workspace");
            folder.applyLock("alice", LocalDateTime.now(), LockLifetime.PERSISTENT, null,
                LockType.WRITE_LOCK, null, false);

            assertTrue(folder.isWriteAllowed("alice", LocalDateTime.now()));
        }

        @Test
        @DisplayName("WRITE_LOCK blocks non-owner")
        void writeLockBlocksNonOwner() {
            Folder folder = folder("workspace");
            folder.applyLock("alice", LocalDateTime.now(), LockLifetime.PERSISTENT, null,
                LockType.WRITE_LOCK, null, false);

            assertFalse(folder.isWriteAllowed("bob", LocalDateTime.now()));
        }

        @Test
        @DisplayName("READ_ONLY_LOCK blocks everyone including owner")
        void readOnlyLockBlocksEveryone() {
            Folder folder = folder("workspace");
            folder.applyLock("alice", LocalDateTime.now(), LockLifetime.PERSISTENT, null,
                LockType.READ_ONLY_LOCK, null, false);

            assertFalse(folder.isWriteAllowed("alice", LocalDateTime.now()));
            assertFalse(folder.isWriteAllowed("bob", LocalDateTime.now()));
        }

        @Test
        @DisplayName("NODE_LOCK blocks update/delete for everyone")
        void nodeLockBlocksAll() {
            Folder folder = folder("workspace");
            folder.applyLock("alice", LocalDateTime.now(), LockLifetime.PERSISTENT, null,
                LockType.NODE_LOCK, null, false);

            assertFalse(folder.isWriteAllowed("alice", LocalDateTime.now()));
        }

        @Test
        @DisplayName("unlocked node allows anyone to write")
        void unlockedAllowsAll() {
            Folder folder = folder("workspace");
            assertTrue(folder.isWriteAllowed("anyone", LocalDateTime.now()));
        }
    }

    // ===================================================================== edge cases

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test
        @DisplayName("lock rejects already locked node")
        void rejectsAlreadyLocked() {
            Folder folder = folder("workspace");
            folder.applyLock("bob", LocalDateTime.now(), LockLifetime.PERSISTENT, null);
            when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(folder.getId(), Node.ArchiveStatus.LIVE))
                .thenReturn(Optional.of(folder));
            when(securityService.hasPermission(folder, PermissionType.WRITE)).thenReturn(true);

            assertThrows(IllegalStateException.class,
                () -> lockService.lock(folder.getId(), LockType.WRITE_LOCK));
        }

        @Test
        @DisplayName("lock rejects without write permission")
        void rejectsWithoutPermission() {
            Folder folder = folder("workspace");
            when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(folder.getId(), Node.ArchiveStatus.LIVE))
                .thenReturn(Optional.of(folder));
            when(securityService.hasPermission(folder, PermissionType.WRITE)).thenReturn(false);

            assertThrows(SecurityException.class,
                () -> lockService.lock(folder.getId(), LockType.WRITE_LOCK));
        }

        @Test
        @DisplayName("lock rejects missing node")
        void rejectsMissingNode() {
            UUID id = UUID.randomUUID();
            when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(id, Node.ArchiveStatus.LIVE)).thenReturn(Optional.empty());

            assertThrows(NoSuchElementException.class,
                () -> lockService.lock(id, LockType.WRITE_LOCK));
        }

        @Test
        @DisplayName("unlock silently returns for unlocked node")
        void unlockSilentForUnlocked() {
            Folder folder = folder("workspace");
            when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(folder.getId(), Node.ArchiveStatus.LIVE))
                .thenReturn(Optional.of(folder));

            assertDoesNotThrow(() -> lockService.unlock(folder.getId()));
            verify(nodeRepository, never()).save(any());
        }
    }

    // ===================================================================== helpers

    private Folder folder(String name) {
        Folder f = new Folder();
        f.setId(UUID.randomUUID());
        f.setName(name);
        f.setPath("/" + name);
        f.setArchiveStatus(Node.ArchiveStatus.LIVE);
        return f;
    }

    private void stubLockable(Folder folder) {
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(folder.getId(), Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.of(folder));
        when(securityService.hasPermission(folder, PermissionType.WRITE)).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("alice");
        when(nodeRepository.save(any(Node.class))).thenAnswer(inv -> inv.getArgument(0));
    }
}
