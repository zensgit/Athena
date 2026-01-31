package com.ecm.core.service;

import com.ecm.core.entity.Document;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Slf4j
@Service
public class VersionLabelService {

    @Value("${ecm.versioning.label-policy:semantic}")
    private String labelPolicy;

    @Value("${ecm.versioning.calendar.format:yyyy.MM.dd}")
    private String calendarFormat;

    @Value("${ecm.versioning.calendar.include-sequence:true}")
    private boolean calendarIncludeSequence;

    @Value("${ecm.versioning.calendar.sequence-separator:.}")
    private String calendarSequenceSeparator;

    public String generateLabel(Document document, int versionNumber) {
        String policy = labelPolicy != null ? labelPolicy.trim().toLowerCase(Locale.ROOT) : "semantic";
        if ("calendar".equals(policy)) {
            String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern(calendarFormat));
            if (calendarIncludeSequence) {
                String separator = calendarSequenceSeparator != null ? calendarSequenceSeparator : ".";
                return datePart + separator + Math.max(versionNumber, 1);
            }
            return datePart;
        }
        if (!"semantic".equals(policy) && !"semver".equals(policy)) {
            log.warn("Unknown version label policy '{}', falling back to semantic", policy);
        }
        int major = document != null && document.getMajorVersion() != null ? document.getMajorVersion() : 1;
        int minor = document != null && document.getMinorVersion() != null ? document.getMinorVersion() : 0;
        return major + "." + minor;
    }
}
