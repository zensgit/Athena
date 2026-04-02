package com.ecm.core.service;

import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Site;
import com.ecm.core.repository.SiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityEventListenerTest {

    @Mock private ActivityService activityService;
    @Mock private SiteRepository siteRepository;

    private ActivityEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new ActivityEventListener(activityService, siteRepository);
    }

    @Nested
    @DisplayName("resolveSiteId")
    class ResolveSiteId {

        @Test
        @DisplayName("returns siteId when node path is under site root folder")
        void matchesNodeUnderSiteRoot() {
            Folder root = new Folder();
            root.setId(UUID.randomUUID());
            root.setPath("/Company/Finance");

            Site site = new Site();
            site.setSiteId("finance");
            site.setRootFolder(root);

            when(siteRepository.findByDeletedFalseOrderByTitleAsc()).thenReturn(List.of(site));

            Folder node = new Folder();
            node.setId(UUID.randomUUID());
            node.setPath("/Company/Finance/Reports/Q1");

            assertEquals("finance", listener.resolveSiteId(node));
        }

        @Test
        @DisplayName("returns siteId when node path equals site root folder")
        void matchesExactRootPath() {
            Folder root = new Folder();
            root.setId(UUID.randomUUID());
            root.setPath("/Company/HR");

            Site site = new Site();
            site.setSiteId("hr");
            site.setRootFolder(root);

            when(siteRepository.findByDeletedFalseOrderByTitleAsc()).thenReturn(List.of(site));

            Folder node = new Folder();
            node.setId(UUID.randomUUID());
            node.setPath("/Company/HR");

            assertEquals("hr", listener.resolveSiteId(node));
        }

        @Test
        @DisplayName("returns null when node is not under any site")
        void returnsNullWhenNoMatch() {
            Folder root = new Folder();
            root.setId(UUID.randomUUID());
            root.setPath("/Company/Finance");

            Site site = new Site();
            site.setSiteId("finance");
            site.setRootFolder(root);

            when(siteRepository.findByDeletedFalseOrderByTitleAsc()).thenReturn(List.of(site));

            Folder node = new Folder();
            node.setId(UUID.randomUUID());
            node.setPath("/Personal/Documents");

            assertNull(listener.resolveSiteId(node));
        }

        @Test
        @DisplayName("returns null for null node")
        void returnsNullForNullNode() {
            assertNull(listener.resolveSiteId(null));
        }

        @Test
        @DisplayName("returns null when no sites have root folders")
        void returnsNullWhenNoRootFolders() {
            Site site = new Site();
            site.setSiteId("empty");
            site.setRootFolder(null);

            when(siteRepository.findByDeletedFalseOrderByTitleAsc()).thenReturn(List.of(site));

            Folder node = new Folder();
            node.setId(UUID.randomUUID());
            node.setPath("/Company/Finance/Reports");

            assertNull(listener.resolveSiteId(node));
        }
    }
}
