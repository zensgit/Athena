package com.ecm.core.service;

import com.ecm.core.entity.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VersionLabelServiceTest {

    @Test
    @DisplayName("Semantic policy uses document major/minor")
    void semanticPolicyUsesDocumentVersion() {
        VersionLabelService service = new VersionLabelService();
        ReflectionTestUtils.setField(service, "labelPolicy", "semantic");

        Document document = new Document();
        document.setMajorVersion(2);
        document.setMinorVersion(7);

        String label = service.generateLabel(document, 3);

        assertEquals("2.7", label);
    }

    @Test
    @DisplayName("Calendar policy uses configured format and sequence")
    void calendarPolicyUsesDateAndSequence() {
        VersionLabelService service = new VersionLabelService();
        ReflectionTestUtils.setField(service, "labelPolicy", "calendar");
        ReflectionTestUtils.setField(service, "calendarFormat", "yyyy.MM.dd");
        ReflectionTestUtils.setField(service, "calendarIncludeSequence", true);
        ReflectionTestUtils.setField(service, "calendarSequenceSeparator", ".");

        String label = service.generateLabel(new Document(), 5);
        String expected = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd")) + ".5";

        assertEquals(expected, label);
    }

    @Test
    @DisplayName("Calendar policy can omit sequence")
    void calendarPolicyOmitsSequence() {
        VersionLabelService service = new VersionLabelService();
        ReflectionTestUtils.setField(service, "labelPolicy", "calendar");
        ReflectionTestUtils.setField(service, "calendarFormat", "yyyyMMdd");
        ReflectionTestUtils.setField(service, "calendarIncludeSequence", false);

        String label = service.generateLabel(new Document(), 12);
        String expected = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        assertEquals(expected, label);
    }

    @Test
    @DisplayName("Unknown policy falls back to semantic")
    void unknownPolicyFallsBackToSemantic() {
        VersionLabelService service = new VersionLabelService();
        ReflectionTestUtils.setField(service, "labelPolicy", "unknown");

        Document document = new Document();
        document.setMajorVersion(4);
        document.setMinorVersion(1);

        String label = service.generateLabel(document, 1);

        assertEquals("4.1", label);
    }
}
