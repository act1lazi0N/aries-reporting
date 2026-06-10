package com.actilazion.ariesreportingproject.entity.reporting;

import com.actilazion.ariesreportingproject.enums.EmailStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "email_logs",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_email_idempotency",
                columnNames = "idempotency_key"
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "billing_month", nullable = false, length = 7)
    private String billingMonth;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EmailStatus status;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "sent_at", nullable = false, updatable = false)
    private OffsetDateTime sentAt;
    public static String buildIdempotencyKey(UUID userId, String billingMonth) {
        return userId.toString() + "::" + billingMonth;
    }
}
