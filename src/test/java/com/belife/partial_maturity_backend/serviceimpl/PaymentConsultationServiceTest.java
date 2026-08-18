package com.belife.partial_maturity_backend.serviceimpl;

import com.belife.partial_maturity_backend.config.properties.InterestProperties;
import com.belife.partial_maturity_backend.dtos.responses.PageResponse;
import com.belife.partial_maturity_backend.dtos.responses.PaymentSummaryResponse;
import com.belife.partial_maturity_backend.entities.PaymentEntity;
import com.belife.partial_maturity_backend.enums.PaymentStatus;
import com.belife.partial_maturity_backend.repositories.PaymentRepository;
import com.belife.partial_maturity_backend.repositories.PolicyMaturityRepository;
import com.belife.partial_maturity_backend.services.AuditService;
import com.belife.partial_maturity_backend.services.BusinessDateProvider;
import com.belife.partial_maturity_backend.services.InterestCalculationEngine;
import com.belife.partial_maturity_backend.services.Impl.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de la consultation paginée
 * des paiements.
 */
@ExtendWith(MockitoExtension.class)
class PaymentConsultationServiceTest {

    @Mock
    private PolicyMaturityRepository policyMaturityRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private InterestCalculationEngine calculationEngine;

    private PaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        InterestProperties interestProperties =
                new InterestProperties(
                        new BigDecimal("0.035")
                );

        Clock clock = Clock.fixed(
                Instant.parse("2026-08-04T10:00:00Z"),
                ZoneOffset.UTC
        );

        BusinessDateProvider businessDateProvider =
                () -> LocalDate.of(
                        2023,
                        3,
                        15
                );

        paymentService =
                new PaymentServiceImpl(
                        policyMaturityRepository,
                        paymentRepository,
                        auditService,
                        calculationEngine,
                        interestProperties,
                        clock,
                        businessDateProvider
                );
    }

    @Test
    @DisplayName("La consultation doit retourner une page légère sans détails")
    void shouldReturnPaymentSummaryPage() {
        PaymentEntity firstPayment =
                payment(
                        12L,
                        "POL002",
                        "2026-08-04",
                        "1500000.000000",
                        PaymentStatus.PAID
                );

        PaymentEntity secondPayment =
                payment(
                        11L,
                        "POL001",
                        "2026-08-03",
                        "1000000.000000",
                        PaymentStatus.CANCELLED
                );

        Page<PaymentEntity> repositoryPage =
                new PageImpl<>(
                        List.of(firstPayment, secondPayment)
                );

        when(
                paymentRepository.findAll(
                        any(Specification.class),
                        any(Pageable.class)
                )
        ).thenReturn(repositoryPage);

        PageResponse<PaymentSummaryResponse>
                response =
                paymentService.searchPayments(
                        null,
                        null,
                        0,
                        20
                );

        assertThat(response.content()).hasSize(2);

        assertThat(response.content())
                .extracting(PaymentSummaryResponse::id)
                .containsExactly(12L, 11L);

        PaymentSummaryResponse first = response.content().getFirst();

        assertThat(first.policyNumber()).isEqualTo("POL002");

        assertThat(first.paidAmount()).isEqualByComparingTo("1500000.000000");

        assertThat(first.status()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    @DisplayName("La page et la taille doivent être normalisées")
    void shouldNormalizePageAndSize() {
        when(
                paymentRepository.findAll(
                        any(Specification.class),
                        any(Pageable.class)
                )
        ).thenReturn(Page.empty());

        paymentService.searchPayments(
                null,
                null,
                -4,
                500
        );

        ArgumentCaptor<Pageable>
                pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);

        verify(paymentRepository).findAll(
                any(Specification.class),
                pageableCaptor.capture()
        );

        Pageable pageable = pageableCaptor.getValue();

        assertThat(pageable.getPageNumber()).isZero();

        assertThat(pageable.getPageSize()).isEqualTo(100);

        assertThat(pageable.getSort().getOrderFor("paymentDate")
        ).isNotNull();

        assertThat(
                pageable.getSort()
                        .getOrderFor("paymentDate")
                        .isDescending()
        ).isTrue();

        assertThat(pageable.getSort().getOrderFor("id")
        ).isNotNull();

        assertThat(
                pageable.getSort()
                        .getOrderFor("id")
                        .isDescending()
        ).isTrue();
    }

    @Test
    @DisplayName("La consultation doit transmettre les filtres au repository")
    void shouldUseRequestedFilters() {
        when(
                paymentRepository.findAll(
                        any(Specification.class),
                        any(Pageable.class)
                )
        ).thenReturn(Page.empty());

        PageResponse<PaymentSummaryResponse>
                response =
                paymentService.searchPayments(
                        " POL001 ",
                        PaymentStatus.PAID,
                        1,
                        10
                );

        assertThat(response.content()).isEmpty();

        verify(paymentRepository).findAll(
                any(Specification.class),
                any(Pageable.class)
        );
    }

    private PaymentEntity payment(
            Long id,
            String policyNumber,
            String paymentDate,
            String paidAmount,
            PaymentStatus status
    ) {
        PaymentEntity payment = new PaymentEntity();

        payment.setId(id);
        payment.setPolicyNumber(policyNumber);
        payment.setPaymentDate(LocalDate.parse(paymentDate));
        payment.setCalculationDate(LocalDate.parse(paymentDate));
        payment.setAnnualRate(new BigDecimal("0.035"));
        payment.setCapitalAmount(new BigDecimal("900000.000000"));
        payment.setInterestAmount(new BigDecimal("100000.000000"));
        payment.setPaidAmount(new BigDecimal(paidAmount));
        payment.setCompletedCycles(3);
        payment.setStatus(status);
        payment.setCreatedAt(Instant.parse("2026-08-04T10:00:00Z"));
        payment.setCreatedBy("accounting");

        return payment;
    }
}