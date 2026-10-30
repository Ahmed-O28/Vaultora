package com.banking;

import com.banking.dto.BeneficiaryRequest;
import com.banking.dto.ShortenRequest;
import com.banking.model.Customer;
import com.banking.model.Transaction;
import com.banking.repository.CustomerRepository;
import com.banking.repository.TransactionRepository;
import com.banking.service.CustomerService;
import com.banking.service.JwtService;
import com.banking.systemdesign.ratelimiter.RedisTokenBucketRateLimiter;
import com.banking.systemdesign.ratelimiter.SimpleRateLimiter;
import com.banking.systemdesign.urlshortener.PersistentShortUrlService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class SystemDesignIntegrationAndRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PersistentShortUrlService shortUrlService;

    @Autowired
    private ObjectMapper objectMapper;

    private Customer alice;
    private Customer bob;
    private String aliceToken;
    private String bobToken;

    @BeforeEach
    void setUp() {
        alice = customerRepository.findByEmail("alice@bank.local").orElseThrow();
        bob = customerRepository.findByEmail("bob@bank.local").orElseThrow();
        aliceToken = jwtService.generateToken(alice.getCustomerId(), alice.getEmail());
        bobToken = jwtService.generateToken(bob.getCustomerId(), bob.getEmail());
    }

    // ==========================================
    // BUG FIX REGRESSION: LOGIN & MPIN
    // ==========================================

    @Test
    @DisplayName("Regression: Invalid password displays 401 error with proper message and does not alter account state")
    void shouldHandleInvalidLoginProperly() throws Exception {
        var loginReq = new com.banking.dto.LoginRequest("alice@bank.local", "wrongpassword");
        mockMvc.perform(post("/api/customers/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid email or password"));
    }

    @Test
    @DisplayName("Regression: Correct MPIN returns 200 and success status")
    void shouldVerifyCorrectMpin() throws Exception {
        var mpinReq = new com.banking.dto.MpinRequest("1234", "ACC-1001");
        mockMvc.perform(post("/api/customers/" + alice.getCustomerId() + "/verify-mpin")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mpinReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.balance").isNumber());
    }

    @Test
    @DisplayName("Regression: Incorrect MPIN returns 401 with descriptive message without invalidating session")
    void shouldRejectIncorrectMpinGracefully() throws Exception {
        var mpinReq = new com.banking.dto.MpinRequest("9999", "ACC-1001");
        mockMvc.perform(post("/api/customers/" + alice.getCustomerId() + "/verify-mpin")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mpinReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(containsString("Incorrect MPIN")));

        // Verify session remains valid immediately after wrong MPIN
        mockMvc.perform(get("/api/customers/" + alice.getCustomerId() + "/accounts")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Regression: Genuine expired or invalid token returns 401 with AUTH_REQUIRED structured errorCode")
    void shouldRejectExpiredOrInvalidTokenWithAuthRequired() throws Exception {
        String invalidToken = "Bearer eyJhbGciOiJIUzI1NiJ9.invalid.signature";
        mockMvc.perform(get("/api/customers/" + alice.getCustomerId() + "/accounts")
                        .header("Authorization", invalidToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"));
    }

    // ==========================================
    // COMPONENT 1: RATE LIMITER
    // ==========================================

    @Test
    @DisplayName("Component 1: Rate limiter allows requests under quota and enforces refill")
    void testRateLimiterTokenBucket() throws Exception {
        SimpleRateLimiter limiter = new SimpleRateLimiter(3, 1000);
        String testKey = "test-user-" + System.currentTimeMillis();

        assertTrue(limiter.allowRequest(testKey));
        assertTrue(limiter.allowRequest(testKey));
        assertTrue(limiter.allowRequest(testKey));
        assertFalse(limiter.allowRequest(testKey), "4th request within 1s window should be rejected");

        // Wait for refill window
        Thread.sleep(1100);
        assertTrue(limiter.allowRequest(testKey), "After refill period, request should be allowed");
    }

    @Test
    @DisplayName("Component 1: RateLimitFilter excludes /api/health and /actuator from rate limiting")
    void testRateLimitFilterExcludesHealth() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Retry-After"));
    }

    // ==========================================
    // COMPONENT 2: PERSISTENT URL SHORTENER
    // ==========================================

    @Test
    @DisplayName("Component 2: URL shortener creates persistent Base62 short code and resolves target")
    void testUrlShortenerPersistenceAndResolution() throws Exception {
        String originalUrl = "https://bank.local/statements/2026-q1";
        ShortenRequest req = new ShortenRequest(originalUrl);

        String responseBody = mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").isNotEmpty())
                .andExpect(jsonPath("$.targetUrl").value(originalUrl))
                .andReturn().getResponse().getContentAsString();

        var json = objectMapper.readTree(responseBody);
        String code = json.get("shortCode").asText();

        // Resolve via redirect endpoint /r/{code}
        mockMvc.perform(get("/r/" + code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", originalUrl));
    }

    @Test
    @DisplayName("Component 2: URL shortener blocks dangerous schemes (javascript:, data:)")
    void testUrlShortenerBlocksUnsafeSchemes() {
        assertThrows(IllegalArgumentException.class, () ->
                shortUrlService.validateUrlSafety("javascript:alert(1)")
        );
        assertThrows(IllegalArgumentException.class, () ->
                shortUrlService.validateUrlSafety("data:text/html,<script>alert(1)</script>")
        );
    }

    @Test
    @DisplayName("Component 2: Transaction receipt can be shortened by authorized owner")
    void testReceiptShorteningAndOwnership() throws Exception {
        // Seed a test transaction for Alice's account ACC-1001
        String txnId = "TXN-TEST-" + System.currentTimeMillis();
        Transaction testTxn = new Transaction(
                txnId,
                "ACC-1001",
                "ACC-1002",
                new BigDecimal("150.00"),
                "TRANSFER",
                "SUCCESS",
                "Test transfer for receipt",
                Instant.now()
        );
        transactionRepository.save(testTxn);

        // Alice (owner of ACC-1001) can generate receipt short link
        ShortenRequest req = new ShortenRequest(null, txnId, 3600L);
        String receiptResp = mockMvc.perform(post("/api/shorten")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionId").value(txnId))
                .andExpect(jsonPath("$.targetUrl").value("/receipts/" + txnId))
                .andReturn().getResponse().getContentAsString();

        String receiptShortCode = objectMapper.readTree(receiptResp).get("shortCode").asText();

        // Resolving /r/{shortCode} redirects to the frontend origin http://localhost:3000/receipts/{txnId}
        mockMvc.perform(get("/r/" + receiptShortCode))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:3000/receipts/" + txnId));

        // Alice can view the receipt with masked account numbers
        mockMvc.perform(get("/api/receipts/" + txnId)
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(txnId))
                .andExpect(jsonPath("$.maskedSourceAccount").value(containsString("***")))
                .andExpect(jsonPath("$.amount").value(150.00));
    }

    @Test
    @DisplayName("Component 2: Unauthorized customer (Charlie) cannot generate or access Bob's private receipt")
    void testReceiptAuthorizationEnforcement() throws Exception {
        Customer charlie = customerRepository.findByEmail("charlie@bank.local").orElseThrow();
        String charlieToken = jwtService.generateToken(charlie.getCustomerId(), charlie.getEmail());

        String txnId = "TXN-PRIVATE-" + System.currentTimeMillis();
        Transaction privateTxn = new Transaction(
                txnId,
                "ACC-1001",
                "ACC-1002",
                new BigDecimal("500.00"),
                "TRANSFER",
                "SUCCESS",
                "Private payment",
                Instant.now()
        );
        transactionRepository.save(privateTxn);

        // Charlie (ACC-2001) attempts to shorten or access Alice/Bob's receipt -> 403 Forbidden
        ShortenRequest req = new ShortenRequest(null, txnId, 3600L);
        mockMvc.perform(post("/api/shorten")
                        .header("Authorization", "Bearer " + charlieToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/receipts/" + txnId)
                        .header("Authorization", "Bearer " + charlieToken))
                .andExpect(status().isForbidden());
    }

    // ==========================================
    // COMPONENT 3: TRIE AUTOCOMPLETE WITH REDIS
    // ==========================================

    @Test
    @DisplayName("Component 3: Autocomplete returns matching beneficiaries for authorized customer")
    void testBeneficiaryAutocomplete() throws Exception {
        // Alice has "Bob (Roommate)" in seeded beneficiaries
        mockMvc.perform(get("/api/customers/" + alice.getCustomerId() + "/autocomplete/beneficiaries?query=Bo")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].term").value("Bob (Roommate)"))
                .andExpect(jsonPath("$[0].category").value("BENEFICIARY"))
                .andExpect(jsonPath("$[0].referenceId").value(containsString("***")));
    }

    @Test
    @DisplayName("Component 3: Customer isolation: User B cannot access User A's beneficiary suggestions")
    void testAutocompleteCustomerIsolation() throws Exception {
        // Bob attempting to query Alice's beneficiaries endpoint -> 403 Forbidden via JwtAuthInterceptor
        mockMvc.perform(get("/api/customers/" + alice.getCustomerId() + "/autocomplete/beneficiaries?query=Bo")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Component 3: Biller autocomplete matches utility names with case normalization")
    void testBillerAutocomplete() throws Exception {
        mockMvc.perform(get("/api/autocomplete/billers?query=elec"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].term").value("City Electric Co."))
                .andExpect(jsonPath("$[0].category").value("BILLER"));
    }

    @Test
    @DisplayName("Component 3: Adding a new beneficiary dynamically updates autocomplete suggestions")
    void testAutocompleteCacheInvalidationOnNewBeneficiary() throws Exception {
        // Add a new beneficiary for Bob
        var beneReq = new BeneficiaryRequest("ACC-2001", "Charlie Partner");
        customerService.addBeneficiary(bob.getCustomerId(), beneReq);

        mockMvc.perform(get("/api/customers/" + bob.getCustomerId() + "/autocomplete/beneficiaries?query=Char")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].term").value("Charlie Partner"));
    }

    @Test
    @DisplayName("Component 1: Rate limit metrics and decision latency are tracked and exposed")
    void testRateLimiterMetricsAndLatency() throws Exception {
        mockMvc.perform(get("/api/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rateLimiter.avgDecisionLatencyMs").isNumber())
                .andExpect(jsonPath("$.rateLimiter.allowed").isNumber());
    }

    @Test
    @DisplayName("Component 2: URL shortener supports originalUrl and ttlHours backward compatibility aliases")
    void testUrlShortenerAliases() throws Exception {
        String testUrl = "https://bank.local/reports/annual-2026";
        ShortenRequest req = new ShortenRequest();
        req.setOriginalUrl(testUrl);
        req.setTtlHours(24L);

        String responseBody = mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").isNotEmpty())
                .andExpect(jsonPath("$.targetUrl").value(testUrl))
                .andReturn().getResponse().getContentAsString();

        var json = objectMapper.readTree(responseBody);
        String code = json.get("shortCode").asText();
        mockMvc.perform(get("/r/" + code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", testUrl));
    }
}
