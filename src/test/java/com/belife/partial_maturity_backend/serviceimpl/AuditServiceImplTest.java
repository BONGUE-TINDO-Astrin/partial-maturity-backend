package com.belife.partial_maturity_backend.serviceimpl;

import com.belife.partial_maturity_backend.entities.AuditLogEntity;
import com.belife.partial_maturity_backend.enums.AuditEventType;
import com.belife.partial_maturity_backend.enums.AuditResourceType;
import com.belife.partial_maturity_backend.exceptions.AuditRecordingException;
import com.belife.partial_maturity_backend.repositories.AuditLogRepository;
import com.belife.partial_maturity_backend.services.Impl.AuditServiceImpl;
import com.belife.partial_maturity_backend.services.models.AuditRecordCommand;
import com.belife.partial_maturity_backend.utils.AuditDetailJsonCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de la création d'une entrée
 * dans le journal d'audit.
 */
@ExtendWith(MockitoExtension.class)
class AuditServiceImplTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private AuditDetailJsonCodec auditDetailJsonCodec;

    private AuditServiceImpl auditService;

    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-07-30T10:00:00Z"), ZoneOffset.UTC);

        auditService =
            new AuditServiceImpl(
                auditLogRepository,
                auditDetailJsonCodec,
                fixedClock
            );
    }

    @Test
    @DisplayName("Une commande valide doit produire une entrée d'audit")
    void shouldRecordAuditEvent() {
        Map<String, Object> details =
            Map.of(
                "paymentId",
                15L,
                "paidAmount",
                "5474140.160000",
                "status",
                "PAID"
            );

        AuditRecordCommand command =
            new AuditRecordCommand(
                AuditEventType.PAYMENT_RECORDED,
                AuditResourceType.PAYMENT,
                "15",
                "POL001",
                "comptable01",
                "Paiement total enregistré.",
                details
            );

        when(auditDetailJsonCodec.write(details)).thenReturn(
            """
            {
              "paymentId": 15,
              "paidAmount": "5474140.160000",
              "status": "PAID"
            }
            """
        );

        when(auditLogRepository.saveAndFlush(any(AuditLogEntity.class))
        ).thenAnswer(invocation -> invocation.getArgument(0));

        AuditLogEntity result = auditService.record(command);

        assertThat(result.getEventType()).isEqualTo(AuditEventType.PAYMENT_RECORDED);

        assertThat(result.getResourceType()).isEqualTo(AuditResourceType.PAYMENT);

        assertThat(result.getResourceId()).isEqualTo("15");

        assertThat(result.getPolicyNumber()).isEqualTo("POL001");

        assertThat(result.getActorUsername()).isEqualTo("comptable01");

        assertThat(result.getSummary()).isEqualTo("Paiement total enregistré.");

        assertThat(result.getOccurredAt()).isEqualTo(Instant.parse("2026-07-30T10:00:00Z"));

        assertThat(result.getDetailJson()).contains("5474140.160000");

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);

        verify(auditLogRepository).saveAndFlush(captor.capture());

        assertThat(captor.getValue().getActorUsername()).isEqualTo("comptable01");

        assertThat(captor.getValue().getOccurredAt()
        ).isEqualTo(Instant.parse("2026-07-30T10:00:00Z"));
    }

    @Test
    @DisplayName("Une police vide doit être normalisée en null")
    void shouldNormalizeBlankPolicyNumberToNull() {
        AuditRecordCommand command =
            new AuditRecordCommand(
                AuditEventType.USER_CREATED,
                AuditResourceType.USER,
                "10",
                "   ",
                "admin",
                "Création d'un compte utilisateur.",
                Map.of()
            );

        when(auditDetailJsonCodec.write(Map.of())
        ).thenReturn(null);

        when(auditLogRepository.saveAndFlush(any(AuditLogEntity.class))
        ).thenAnswer(invocation -> invocation.getArgument(0));

        AuditLogEntity result = auditService.record(command);

        assertThat(result.getPolicyNumber()).isNull();

        assertThat(result.getDetailJson()).isNull();
    }

    @Test
    @DisplayName("Une commande sans acteur doit être refusée")
    void shouldRejectMissingActor() {
        AuditRecordCommand command =
            new AuditRecordCommand(
                AuditEventType.FILE_IMPORTED,
                AuditResourceType.IMPORT_BATCH,
                "20",
                null,
                " ",
                "Importation d'un fichier.",
                Map.of()
            );

        assertThatThrownBy(() -> auditService.record(command))
            .isInstanceOf(AuditRecordingException.class)
            .hasMessageContaining("utilisateur");

        verifyNoInteractions(auditLogRepository, auditDetailJsonCodec);
    }

    @Test
    @DisplayName("Une commande sans identifiant de ressource doit être refusée")
    void shouldRejectMissingResourceId() {
        AuditRecordCommand command =
            new AuditRecordCommand(
                AuditEventType.FILE_REJECTED,
                AuditResourceType.IMPORT_BATCH,
                "",
                null,
                "admin",
                "Rejet d'un fichier CSV.",
                Map.of()
            );

        assertThatThrownBy(() -> auditService.record(command)
        )
            .isInstanceOf(AuditRecordingException.class)
            .hasMessageContaining("identifiant");

        verifyNoInteractions(auditLogRepository, auditDetailJsonCodec);
    }
}