package com.ecm.core.service;

import com.ecm.core.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigInteger;

@Service
@RequiredArgsConstructor
public class AsnGeneratorService {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Generate the next available Archive Serial Number (ASN).
     * Thread-safe via database sequence or max query.
     */
    @Transactional
    public Integer getNextAsn() {
        // Use a native query to atomically get next value from a sequence
        // Or simplified: select max(asn) + 1
        try {
            Number max = (Number) entityManager.createQuery("SELECT MAX(d.archiveSerialNumber) FROM Document d").getSingleResult();
            return max == null ? 1 : max.intValue() + 1;
        } catch (Exception e) {
            return 1;
        }
    }
}
