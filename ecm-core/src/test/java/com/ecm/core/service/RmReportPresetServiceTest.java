package com.ecm.core.service;

import com.ecm.core.entity.RmReportPreset;
import com.ecm.core.entity.RmReportPreset.Kind;
import com.ecm.core.repository.RmReportPresetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RmReportPresetService")
class RmReportPresetServiceTest {

    @Mock
    private RmReportPresetRepository repository;

    @Mock
    private SecurityService securityService;

    private RmReportPresetService service;

    @BeforeEach
    void setUp() {
        service = new RmReportPresetService(repository, securityService);
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("persists new preset owned by current user")
        void persistsNewPreset() {
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(repository.existsByOwnerAndNameAndDeletedFalse("alice", "Q1 families"))
                    .thenReturn(false);
            when(repository.save(any(RmReportPreset.class))).thenAnswer(inv -> inv.getArgument(0));

            RmReportPreset result = service.create(
                    "Q1 families",
                    "Top families for Q1 review",
                    Kind.ACTIVITY_FAMILY_REPORT,
                    Map.of("from", "2026-01-01", "to", "2026-03-31")
            );

            assertEquals("alice", result.getOwner());
            assertEquals("Q1 families", result.getName());
            assertEquals(Kind.ACTIVITY_FAMILY_REPORT, result.getKind());
            assertEquals("2026-01-01", result.getParams().get("from"));
        }

        @Test
        @DisplayName("trims whitespace from name and description")
        void trimsNameAndDescription() {
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(repository.existsByOwnerAndNameAndDeletedFalse("alice", "Q1"))
                    .thenReturn(false);
            when(repository.save(any(RmReportPreset.class))).thenAnswer(inv -> inv.getArgument(0));

            RmReportPreset result = service.create(
                    "  Q1  ",
                    "  description  ",
                    Kind.ACTIVITY_FAMILY_REPORT,
                    null
            );

            assertEquals("Q1", result.getName());
            assertEquals("description", result.getDescription());
        }

        @Test
        @DisplayName("rejects duplicate (owner, name)")
        void rejectsDuplicateName() {
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(repository.existsByOwnerAndNameAndDeletedFalse("alice", "Q1"))
                    .thenReturn(true);

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> service.create("Q1", null, Kind.ACTIVITY_FAMILY_REPORT, null));
            assertTrue(ex.getMessage().contains("already exists"));
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("rejects blank name")
        void rejectsBlankName() {
            when(securityService.getCurrentUser()).thenReturn("alice");

            assertThrows(IllegalArgumentException.class,
                    () -> service.create("   ", null, Kind.ACTIVITY_FAMILY_REPORT, null));
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("rejects null kind")
        void rejectsNullKind() {
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(repository.existsByOwnerAndNameAndDeletedFalse(anyString(), anyString()))
                    .thenReturn(false);

            assertThrows(IllegalArgumentException.class,
                    () -> service.create("Q1", null, null, null));
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("rejects missing authenticated user")
        void rejectsMissingUser() {
            when(securityService.getCurrentUser()).thenReturn(null);

            assertThrows(SecurityException.class,
                    () -> service.create("Q1", null, Kind.ACTIVITY_FAMILY_REPORT, null));
        }
    }

    @Nested
    @DisplayName("listForCurrentUser")
    class List {

        @Test
        @DisplayName("returns owned presets ordered by name")
        void returnsOwned() {
            when(securityService.getCurrentUser()).thenReturn("alice");
            RmReportPreset a = RmReportPreset.builder().owner("alice").name("A").build();
            when(repository.findByOwnerAndDeletedFalseOrderByName("alice"))
                    .thenReturn(java.util.List.of(a));

            java.util.List<RmReportPreset> result = service.listForCurrentUser();

            assertEquals(1, result.size());
            assertEquals("A", result.get(0).getName());
        }

        @Test
        @DisplayName("returns empty list when no user")
        void returnsEmptyWhenAnonymous() {
            when(securityService.getCurrentUser()).thenReturn("");

            assertTrue(service.listForCurrentUser().isEmpty());
            verify(repository, never()).findByOwnerAndDeletedFalseOrderByName(anyString());
        }
    }

    @Nested
    @DisplayName("getOwned")
    class GetOwned {

        @Test
        @DisplayName("returns preset when owner matches")
        void returnsWhenOwnerMatches() {
            UUID id = UUID.randomUUID();
            RmReportPreset preset = RmReportPreset.builder().owner("alice").name("A").build();
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(repository.findByIdAndDeletedFalse(id)).thenReturn(Optional.of(preset));

            assertSame(preset, service.getOwned(id));
        }

        @Test
        @DisplayName("rejects when owner differs")
        void rejectsWhenOwnerDiffers() {
            UUID id = UUID.randomUUID();
            RmReportPreset preset = RmReportPreset.builder().owner("bob").name("A").build();
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(repository.findByIdAndDeletedFalse(id)).thenReturn(Optional.of(preset));

            assertThrows(SecurityException.class, () -> service.getOwned(id));
        }

        @Test
        @DisplayName("404 when not found")
        void notFound() {
            UUID id = UUID.randomUUID();
            when(repository.findByIdAndDeletedFalse(id)).thenReturn(Optional.empty());

            assertThrows(NoSuchElementException.class, () -> service.getOwned(id));
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("soft-deletes owned preset")
        void softDeletes() {
            UUID id = UUID.randomUUID();
            RmReportPreset preset = RmReportPreset.builder().owner("alice").name("A").build();
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(repository.findByIdAndDeletedFalse(id)).thenReturn(Optional.of(preset));
            when(repository.save(preset)).thenReturn(preset);

            service.delete(id);

            assertTrue(preset.isDeleted());
            assertNotNull(preset.getDeletedAt());
            assertEquals("alice", preset.getDeletedBy());
            verify(repository).save(preset);
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("updates name when unique")
        void updatesName() {
            UUID id = UUID.randomUUID();
            RmReportPreset preset = RmReportPreset.builder().owner("alice").name("A").build();
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(repository.findByIdAndDeletedFalse(id)).thenReturn(Optional.of(preset));
            when(repository.existsByOwnerAndNameAndDeletedFalse("alice", "B")).thenReturn(false);
            when(repository.save(preset)).thenReturn(preset);

            RmReportPreset result = service.update(id, "B", null, null);

            assertEquals("B", result.getName());
        }

        @Test
        @DisplayName("rejects rename to another existing preset name")
        void rejectsDuplicateRename() {
            UUID id = UUID.randomUUID();
            RmReportPreset preset = RmReportPreset.builder().owner("alice").name("A").build();
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(repository.findByIdAndDeletedFalse(id)).thenReturn(Optional.of(preset));
            when(repository.existsByOwnerAndNameAndDeletedFalse("alice", "B")).thenReturn(true);

            assertThrows(IllegalArgumentException.class,
                    () -> service.update(id, "B", null, null));
        }
    }
}
