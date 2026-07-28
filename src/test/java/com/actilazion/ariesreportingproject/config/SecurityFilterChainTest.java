package com.actilazion.ariesreportingproject.config;

import com.actilazion.ariesreportingproject.dto.response.TransactionSummaryResponse;
import com.actilazion.ariesreportingproject.entity.reporting.ReportingTransaction;
import com.actilazion.ariesreportingproject.service.export.ExportOrchestrator;
import com.actilazion.ariesreportingproject.service.reporting.AdminReportService;
import com.actilazion.ariesreportingproject.service.reporting.SpendingPatternService;
import com.actilazion.ariesreportingproject.service.reporting.StatementService;
import com.actilazion.ariesreportingproject.service.security.UserAccessService;
import com.actilazion.ariesreportingproject.service.sync.BackfillService;
import com.actilazion.ariesreportingproject.support.ApplicationTestPropertiesInitializer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = ApplicationTestPropertiesInitializer.class)
@Import(SecurityFilterChainTest.TestBeans.class)
@TestPropertySource(properties = {
        "jwt.secret=dGVzdFNlY3JldEtleUZvckp3dFRlc3RpbmdQdXJwb3Nlc09ubHk=",
        "jwt.issuer=aries-transaction",
        "jwt.audience=aries-transaction-api",
        "jwt.token-type=access",
        "jwt.clock-skew-seconds=30"
})
class SecurityFilterChainTest {
    private static final String SECRET =
            "dGVzdFNlY3JldEtleUZvckp3dFRlc3RpbmdQdXJwb3Nlc09ubHk=";
    private static final String WRONG_SECRET =
            "d3JvbmdTZWNyZXRLZXlGb3JKd3RUZXN0aW5nUHVycG9zZU9ubHk=";
    private static final String USER_EMAIL = "user@aries.local";
    private static final String ADMIN_EMAIL = "admin@aries.local";
    private static final String AUDITOR_EMAIL = "auditor@aries.local";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserDetailsService userDetailsService;
    @Autowired
    StatementService statementService;
    @Autowired
    UserAccessService userAccessService;
    @Autowired
    BackfillService backfillService;

    @AfterEach
    void resetMocks() {
        SecurityContextHolder.clearContext();
        reset(userDetailsService, statementService, userAccessService, backfillService);
    }

    @Test
    @DisplayName("protected endpoint without token returns 401")
    void protectedEndpoint_missingToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(statementRequest())
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }

    @Test
    @DisplayName("empty bearer token returns 401 with bearer challenge")
    void protectedEndpoint_emptyBearer_returnsUnauthorized() throws Exception {
        mockMvc.perform(statementRequest()
                        .header(HttpHeaders.AUTHORIZATION, "Bearer "))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }

    @Test
    @DisplayName("malformed bearer token returns 401 with bearer challenge")
    void protectedEndpoint_malformedToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(statementRequest()
                        .header(HttpHeaders.AUTHORIZATION, "Bearer malformed"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }

    @Test
    @DisplayName("wrong issuer token returns 401")
    void protectedEndpoint_wrongIssuer_returnsUnauthorized() throws Exception {
        mockMvc.perform(statementRequest()
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(USER_EMAIL, "wrong-issuer", "aries-transaction-api", "access")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }

    @Test
    @DisplayName("tampered JWT payload returns 401")
    void protectedEndpoint_tamperedPayload_returnsUnauthorized() throws Exception {
        mockMvc.perform(statementRequest()
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tamperedPayloadToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }

    @Test
    @DisplayName("wrong signing key returns 401")
    void protectedEndpoint_wrongSigningKey_returnsUnauthorized() throws Exception {
        mockMvc.perform(statementRequest()
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(USER_EMAIL, "aries-transaction",
                                        "aries-transaction-api", "access", WRONG_SECRET)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }

    @Test
    @DisplayName("future not-before returns 401")
    void protectedEndpoint_futureNotBefore_returnsUnauthorized() throws Exception {
        mockMvc.perform(statementRequest()
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithFutureNotBefore()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }

    @Test
    @DisplayName("wrong audience returns 401")
    void protectedEndpoint_wrongAudience_returnsUnauthorized() throws Exception {
        mockMvc.perform(statementRequest()
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(USER_EMAIL, "aries-transaction", "wrong-audience", "access")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }

    @Test
    @DisplayName("missing audience returns 401")
    void protectedEndpoint_missingAudience_returnsUnauthorized() throws Exception {
        mockMvc.perform(statementRequest()
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithoutAudience()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }

    @Test
    @DisplayName("missing issuer returns 401")
    void protectedEndpoint_missingIssuer_returnsUnauthorized() throws Exception {
        mockMvc.perform(statementRequest()
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithoutIssuer()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }

    @Test
    @DisplayName("refresh token type returns 401")
    void protectedEndpoint_refreshToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(statementRequest()
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(USER_EMAIL, "aries-transaction",
                                        "aries-transaction-api", "refresh")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }

    @Test
    @DisplayName("locked user token returns 401")
    void protectedEndpoint_lockedUser_returnsUnauthorized() throws Exception {
        when(userDetailsService.loadUserByUsername(USER_EMAIL))
                .thenReturn(User.withUsername(USER_EMAIL)
                        .password("n/a")
                        .roles("USER")
                        .accountLocked(true)
                        .build());

        mockMvc.perform(statementRequest()
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(USER_EMAIL)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }

    @Test
    @DisplayName("user token cannot access admin route")
    void adminRoute_userToken_returnsForbidden() throws Exception {
        when(userDetailsService.loadUserByUsername(USER_EMAIL))
                .thenReturn(activeUser(USER_EMAIL, "USER"));

        mockMvc.perform(post("/api/v1/admin/backfill")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(USER_EMAIL)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("admin token can access admin route")
    void adminRoute_adminToken_returnsOk() throws Exception {
        when(userDetailsService.loadUserByUsername(ADMIN_EMAIL))
                .thenReturn(activeUser(ADMIN_EMAIL, "ADMIN"));
        when(backfillService.backfill(any())).thenReturn(2L);

        mockMvc.perform(post("/api/v1/admin/backfill")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(ADMIN_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(2));
    }

    @Test
    @DisplayName("auditor token can read account report routes")
    void reportRoute_auditorToken_returnsOk() throws Exception {
        UUID accountId = UUID.randomUUID();
        when(userDetailsService.loadUserByUsername(AUDITOR_EMAIL))
                .thenReturn(activeUser(AUDITOR_EMAIL, "AUDITOR"));
        when(statementService.getMonthlySummary(accountId, 2026, 1))
                .thenReturn(TransactionSummaryResponse.builder()
                        .accountId(accountId)
                        .year(2026)
                        .month(1)
                        .totalDebit(BigDecimal.ZERO)
                        .totalCredit(BigDecimal.ZERO)
                        .txCount(0)
                        .openingBalance(BigDecimal.ZERO)
                        .closingBalance(BigDecimal.ZERO)
                        .isFromSnapshot(false)
                        .build());

        mockMvc.perform(get("/api/v1/reports/summary/monthly")
                        .param("accountId", accountId.toString())
                        .param("year", "2026")
                        .param("month", "1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(AUDITOR_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountId").value(accountId.toString()));
        verify(userAccessService).requireAccountAccess(any(), eq(accountId));
    }

    @Test
    @DisplayName("top transactions API returns DTO response for authorized user")
    void topTransactions_userToken_returnsDtoResponse() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();
        UUID originalTxId = UUID.randomUUID();
        OffsetDateTime from = OffsetDateTime.parse("2026-07-01T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2026-08-01T00:00:00Z");
        when(userDetailsService.loadUserByUsername(USER_EMAIL))
                .thenReturn(activeUser(USER_EMAIL, "USER"));
        when(statementService.getTopTransactions(accountId, from, to, 5))
                .thenReturn(List.of(ReportingTransaction.builder()
                        .id(txId)
                        .originalTxId(originalTxId)
                        .fromAccountNumber("1001")
                        .toAccountNumber("2002")
                        .fromOwnerName("Sender")
                        .toOwnerName("Receiver")
                        .amount(new BigDecimal("999.00"))
                        .currency("VND")
                        .status("COMPLETED")
                        .description("top")
                        .createdAt(from.plusDays(1))
                        .build()));

        mockMvc.perform(get("/api/v1/reports/top-transactions")
                        .param("accountId", accountId.toString())
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .param("limit", "5")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(USER_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(txId.toString()))
                .andExpect(jsonPath("$.data[0].originalTxId").value(originalTxId.toString()))
                .andExpect(jsonPath("$.data[0].amount").value(999.00))
                .andExpect(jsonPath("$.data[0].fromAccountId").doesNotExist())
                .andExpect(jsonPath("$.data[0].syncedAt").doesNotExist());
        verify(userAccessService).requireAccountAccess(any(), eq(accountId));
        verify(statementService).getTopTransactions(accountId, from, to, 5);
    }

    @Test
    @DisplayName("top transactions API denies cross-account access")
    void topTransactions_crossAccount_returnsForbidden() throws Exception {
        UUID accountId = UUID.randomUUID();
        OffsetDateTime from = OffsetDateTime.parse("2026-07-01T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2026-08-01T00:00:00Z");
        when(userDetailsService.loadUserByUsername(USER_EMAIL))
                .thenReturn(activeUser(USER_EMAIL, "USER"));
        doThrow(new AccessDeniedException("Account access denied"))
                .when(userAccessService).requireAccountAccess(any(), eq(accountId));

        mockMvc.perform(get("/api/v1/reports/top-transactions")
                        .param("accountId", accountId.toString())
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .param("limit", "5")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(USER_EMAIL)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("auditor token cannot access export routes")
    void exportRoute_auditorToken_returnsForbidden() throws Exception {
        when(userDetailsService.loadUserByUsername(AUDITOR_EMAIL))
                .thenReturn(activeUser(AUDITOR_EMAIL, "AUDITOR"));

        mockMvc.perform(get("/api/v1/reports/export/{jobId}/status", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(AUDITOR_EMAIL)))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder statementRequest() {
        return get("/api/v1/reports/statement")
                .param("accountId", UUID.randomUUID().toString())
                .param("from", "2026-01")
                .param("to", "2026-01");
    }

    private org.springframework.security.core.userdetails.UserDetails activeUser(String email, String role) {
        return User.withUsername(email)
                .password("n/a")
                .roles(role)
                .build();
    }

    private String token(String subject) {
        return token(subject, "aries-transaction", "aries-transaction-api", "access");
    }

    private String token(String subject, String issuer, String audience, String tokenType) {
        return token(subject, issuer, audience, tokenType, SECRET);
    }

    private String token(String subject, String issuer, String audience, String tokenType, String secret) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .issuer(issuer)
                .audience()
                .add(audience)
                .and()
                .claim("typ", tokenType)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret)), Jwts.SIG.HS256)
                .compact();
    }

    private String tokenWithoutAudience() {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(USER_EMAIL)
                .issuer("aries-transaction")
                .claim("typ", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)), Jwts.SIG.HS256)
                .compact();
    }

    private String tokenWithoutIssuer() {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(USER_EMAIL)
                .audience()
                .add("aries-transaction-api")
                .and()
                .claim("typ", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)), Jwts.SIG.HS256)
                .compact();
    }

    private String tokenWithFutureNotBefore() {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(USER_EMAIL)
                .issuer("aries-transaction")
                .audience()
                .add("aries-transaction-api")
                .and()
                .claim("typ", "access")
                .issuedAt(Date.from(now))
                .notBefore(Date.from(now.plusSeconds(120)))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)), Jwts.SIG.HS256)
                .compact();
    }

    private String tamperedPayloadToken() {
        String[] parts = token(USER_EMAIL).split("\\.");
        String payload = "{\"sub\":\"attacker@aries.local\",\"iss\":\"aries-transaction\","
                + "\"aud\":[\"aries-transaction-api\"],\"typ\":\"access\","
                + "\"iat\":1780000000,\"exp\":1990000000}";
        parts[1] = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return String.join(".", parts);
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        @Primary
        UserDetailsService userDetailsService() {
            return mock(UserDetailsService.class);
        }

        @Bean
        @Primary
        StatementService statementService() {
            return mock(StatementService.class);
        }

        @Bean
        @Primary
        AdminReportService adminReportService() {
            return mock(AdminReportService.class);
        }

        @Bean
        @Primary
        SpendingPatternService spendingPatternService() {
            return mock(SpendingPatternService.class);
        }

        @Bean
        @Primary
        UserAccessService userAccessService() {
            return mock(UserAccessService.class);
        }

        @Bean
        @Primary
        ExportOrchestrator exportOrchestrator() {
            return mock(ExportOrchestrator.class);
        }

        @Bean
        @Primary
        BackfillService backfillService() {
            return mock(BackfillService.class);
        }
    }
}
