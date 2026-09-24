package com.belife.partial_maturity_backend.services.Impl;

import com.belife.partial_maturity_backend.dtos.responses.AuditLogDetailResponse;
import com.belife.partial_maturity_backend.dtos.responses.AuditLogSummaryResponse;
import com.belife.partial_maturity_backend.dtos.responses.PageResponse;
import com.belife.partial_maturity_backend.entities.AuditLogEntity;
import com.belife.partial_maturity_backend.enums.AuditEventType;
import com.belife.partial_maturity_backend.enums.AuditResourceType;
import com.belife.partial_maturity_backend.exceptions.AuditLogNotFoundException;
import com.belife.partial_maturity_backend.exceptions.InvalidAuditFilterException;
import com.belife.partial_maturity_backend.repositories.AuditLogRepository;
import com.belife.partial_maturity_backend.repositories.specifications.AuditLogSpecifications;
import com.belife.partial_maturity_backend.services.AuditConsultationService;
import com.belife.partial_maturity_backend.utils.AuditDetailJsonCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Implémente la consultation filtrée du journal d'audit.
 *
 * <p>Toutes les opérations sont en lecture seule.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditConsultationServiceImpl implements AuditConsultationService {

    private static final int MAXIMUM_PAGE_SIZE = 100;

    private final AuditLogRepository auditLogRepository;
    private final AuditDetailJsonCodec auditDetailJsonCodec;

    @Override
    public PageResponse<AuditLogSummaryResponse> search(
        AuditEventType eventType,
        AuditResourceType resourceType,
        String actor,
        String policyNumber,
        Instant from,
        Instant to,
        int page,
        int size
    ) {
        validatePeriod(from, to);

        int safePage = Math.max(page, 0);

        int safeSize = Math.min(Math.max(size, 1), MAXIMUM_PAGE_SIZE);

        Sort sort = Sort.by(
            Sort.Order.desc("occurredAt"),
            Sort.Order.desc("id")
        );

        Pageable pageable = PageRequest.of(safePage, safeSize, sort);

        Specification<AuditLogEntity> specification = Specification
                .where(AuditLogSpecifications.hasEventType(eventType))
                .and(AuditLogSpecifications.hasResourceType(resourceType))
                .and(AuditLogSpecifications.hasActor(actor))
                .and(AuditLogSpecifications.hasPolicyNumber(policyNumber))
                .and(AuditLogSpecifications.occurredAtOrAfter(from))
                .and(AuditLogSpecifications.occurredAtOrBefore(to));

        Page<AuditLogEntity> auditPage = auditLogRepository.findAll(specification, pageable);

        List<AuditLogSummaryResponse> content = auditPage.getContent()
                .stream()
                .map(this::toSummaryResponse)
                .toList();

        return PageResponse.from(auditPage, content);
    }

    @Override
    public AuditLogDetailResponse getById(Long auditId) {
        AuditLogEntity auditLog =
            auditLogRepository
                .findById(auditId)
                .orElseThrow( () -> new AuditLogNotFoundException(auditId) );

        return new AuditLogDetailResponse(
            auditLog.getId(),
            auditLog.getEventType(),
            auditLog.getResourceType(),
            auditLog.getResourceId(),
            auditLog.getPolicyNumber(),
            auditLog.getActorUsername(),
            auditLog.getOccurredAt(),
            auditLog.getSummary(),
            auditDetailJsonCodec.read(auditLog.getDetailJson())
        );
    }

    private AuditLogSummaryResponse toSummaryResponse(AuditLogEntity auditLog) {
        return new AuditLogSummaryResponse(
            auditLog.getId(),
            auditLog.getEventType(),
            auditLog.getResourceType(),
            auditLog.getResourceId(),
            auditLog.getPolicyNumber(),
            auditLog.getActorUsername(),
            auditLog.getOccurredAt(),
            auditLog.getSummary()
        );
    }

    private void validatePeriod(Instant from, Instant to) {
        if (from != null && to != null  && from.isAfter(to)
        ) {
            throw new InvalidAuditFilterException(
                    "La date de début doit être antérieure ou égale à la date de fin."
            );
        }
    }
}