package com.pharmacy.common;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class ReferenceGenerator {

    private final EntityManager entityManager;

    public String next(String prefix, LocalDate date) {
        String key = prefix + "-" + date.format(DateTimeFormatter.BASIC_ISO_DATE);
        Number value = (Number) entityManager.createNativeQuery("""
                        INSERT INTO document_sequences (seq_key, last_value)
                        VALUES (:key, 1)
                        ON CONFLICT (seq_key)
                        DO UPDATE SET last_value = document_sequences.last_value + 1
                        RETURNING last_value
                        """)
                .setParameter("key", key)
                .getSingleResult();
        return key + "-" + String.format("%04d", value.longValue());
    }
}
