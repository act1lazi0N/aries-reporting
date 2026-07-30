package com.actilazion.ariesreportingproject.service.export;

import com.actilazion.ariesreportingproject.config.AppProperties;
import com.actilazion.ariesreportingproject.dto.response.AccountStatementResponse;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {
    @Mock
    JavaMailSender mailSender;
    @Mock
    TemplateEngine templateEngine;

    @Test
    @DisplayName("sendMonthlyStatement: builds links from configured public base URL")
    void sendMonthlyStatement_usesConfiguredPublicBaseUrl() throws Exception {
        AppProperties appProperties = new AppProperties();
        appProperties.setMailFrom("noreply@aries.local");
        appProperties.setPublicBaseUrl("https://reports.example.com/");
        EmailService service = new EmailService(mailSender, templateEngine, appProperties);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));

        when(mailSender.createMimeMessage()).thenReturn(message);
        when(templateEngine.process(eq("email/monthly-statement"), org.mockito.ArgumentMatchers.any(Context.class)))
                .thenReturn("<html></html>");

        service.sendMonthlyStatement(
                "user@example.test",
                "User One",
                "2026-07",
                statement(),
                List.of(),
                "user-id::2026-07");

        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("email/monthly-statement"), contextCaptor.capture());
        assertThat(contextCaptor.getValue().getVariable("dashboardUrl"))
                .isEqualTo("https://reports.example.com/dashboard");
        assertThat(contextCaptor.getValue().getVariable("unsubscribeUrl"))
                .isEqualTo("https://reports.example.com/unsubscribe");
        assertThat(message.getHeader("X-Aries-Idempotency-Key", null))
                .isEqualTo("user-id::2026-07");
        assertThat(message.getHeader("Message-ID", null))
                .isEqualTo("<" + UUID.nameUUIDFromBytes(
                        "user-id::2026-07".getBytes(StandardCharsets.UTF_8)) + "@aries-reporting>");
        verify(mailSender).send(message);
    }

    private AccountStatementResponse statement() {
        return AccountStatementResponse.builder()
                .totalDebit(BigDecimal.ZERO)
                .totalCredit(BigDecimal.ZERO)
                .netFlow(BigDecimal.ZERO)
                .txCount(0)
                .build();
    }
}
