package com.ecm.core.service;

import com.ecm.core.entity.Correspondent;
import com.ecm.core.repository.CorrespondentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class CorrespondentService {

    private final CorrespondentRepository correspondentRepository;

    @Transactional(readOnly = true)
    public Page<Correspondent> getCorrespondents(Pageable pageable) {
        return correspondentRepository.findAll(pageable);
    }

    @Transactional
    public Correspondent createCorrespondent(Correspondent correspondent) {
        if (correspondentRepository.findByName(correspondent.getName()).isPresent()) {
            throw new IllegalArgumentException("Correspondent already exists: " + correspondent.getName());
        }
        return correspondentRepository.save(correspondent);
    }

    @Transactional
    public Correspondent updateCorrespondent(java.util.UUID id, Correspondent updates) {
        Correspondent existing = correspondentRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Correspondent not found"));
        
        if (updates.getName() != null) existing.setName(updates.getName());
        if (updates.getMatchAlgorithm() != null) existing.setMatchAlgorithm(updates.getMatchAlgorithm());
        if (updates.getMatchPattern() != null) existing.setMatchPattern(updates.getMatchPattern());
        existing.setInsensitive(updates.isInsensitive());
        
        return correspondentRepository.save(existing);
    }

    @Transactional(readOnly = true)
    public Correspondent matchCorrespondent(String content) {
        if (content == null || content.isEmpty()) return null;
        
        // Naive implementation: iterate all correspondents with patterns
        // In prod: cache patterns or use a more efficient search (e.g. Aho-Corasick for literals)
        List<Correspondent> candidates = correspondentRepository.findByMatchPatternIsNotNullAndMatchPatternNot("");
        
        for (Correspondent c : candidates) {
            if (matches(c, content)) {
                return c;
            }
        }
        return null;
    }

    private boolean matches(Correspondent c, String content) {
        String pattern = c.getMatchPattern();
        String algo = c.getMatchAlgorithm();
        boolean caseInsensitive = c.isInsensitive();
        
        String target = caseInsensitive ? content.toLowerCase() : content;
        String p = caseInsensitive ? pattern.toLowerCase() : pattern;

        switch (algo) {
            case "ANY": // Any word matches
                return Arrays.stream(p.split("\\s+")).anyMatch(target::contains);
            case "ALL": // All words must match
                return Arrays.stream(p.split("\\s+")).allMatch(target::contains);
            case "EXACT":
                return target.equals(p);
            case "REGEX":
                try {
                    int flags = caseInsensitive ? Pattern.CASE_INSENSITIVE : 0;
                    return Pattern.compile(pattern, flags).matcher(content).find();
                } catch (Exception e) {
                    log.warn("Invalid regex for correspondent {}: {}", c.getName(), pattern);
                    return false;
                }
            case "FUZZY":
                // Basic implementation: check if string contains pattern with some tolerance?
                // For now, fallback to contains
                return target.contains(p);
            case "AUTO":
            default:
                // Default to ANY
                return target.contains(p);
        }
    }
}
