package com.banking;

import com.banking.dto.BeneficiaryRequest;
import com.banking.dto.BillPaymentRequest;
import com.banking.dto.LoginRequest;
import com.banking.dto.RegisterRequest;
import com.banking.model.Account;
import com.banking.model.Customer;
import com.banking.repository.AccountRepository;
import com.banking.repository.CustomerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CustomerAndBillPaymentTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    @DisplayName("Customer registration should hash password with BCrypt and reject duplicate email")
    void testCustomerRegistrationAndPasswordHash() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("Test User", "testuser@bank.local", "secretPassword99");

        mockMvc.perform(post("/api/customers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").isNumber())
                .andExpect(jsonPath("$.email").value("testuser@bank.local"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        // Verify password is stored as BCrypt hash in DB, never plaintext
        Optional<Customer> savedCustomer = customerRepository.findByEmail("testuser@bank.local");
        assertTrue(savedCustomer.isPresent());
        String hash = savedCustomer.get().getPasswordHash();
        assertNotEquals("secretPassword99", hash, "Password must not be stored in plaintext");
        assertTrue(hash.startsWith("$2a$") || hash.startsWith("$2b$"), "Password must be BCrypt hashed");

        // Verify duplicate email is rejected with HTTP 409 Conflict
        mockMvc.perform(post("/api/customers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(containsString("already exists")));
    }

    @Test
    @DisplayName("Customer authentication should verify credentials")
    void testCustomerAuthentication() throws Exception {
        // Alice Smith is pre-seeded with password123
        LoginRequest validLogin = new LoginRequest("alice@bank.local", "password123");
        mockMvc.perform(post("/api/customers/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@bank.local"))
                .andExpect(jsonPath("$.fullName").value("Alice Smith"));

        // Wrong password should be rejected with HTTP 401
        LoginRequest wrongLogin = new LoginRequest("alice@bank.local", "wrongpassword");
        mockMvc.perform(post("/api/customers/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongLogin)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(containsString("Invalid email or password")));
    }

    @Autowired
    private com.banking.service.JwtService jwtService;

    @Test
    @DisplayName("Customer accounts endpoint should return accounts owned by customer")
    void testCustomerAccounts() throws Exception {
        Customer alice = customerRepository.findByEmail("alice@bank.local").orElseThrow();
        String token = jwtService.generateToken(alice.getCustomerId(), alice.getEmail());

        mockMvc.perform(get("/api/customers/" + alice.getCustomerId() + "/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].accountNumber").value("ACC-1001"));
    }

    @Test
    @DisplayName("Beneficiary operations should validate accounts and prevent duplicates")
    void testBeneficiaryOperations() throws Exception {
        Customer diana = customerRepository.findByEmail("diana@bank.local").orElseThrow();
        String token = jwtService.generateToken(diana.getCustomerId(), diana.getEmail());

        // Valid beneficiary
        BeneficiaryRequest req = new BeneficiaryRequest("ACC-1001", "Alice Work");
        mockMvc.perform(post("/api/customers/" + diana.getCustomerId() + "/beneficiaries")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.beneficiaryAccountNumber").value("ACC-1001"));

        // Duplicate beneficiary rejected
        mockMvc.perform(post("/api/customers/" + diana.getCustomerId() + "/beneficiaries")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(containsString("already registered")));

        // Non-existent account rejected
        BeneficiaryRequest invalidAccReq = new BeneficiaryRequest("ACC-9999", "Ghost");
        mockMvc.perform(post("/api/customers/" + diana.getCustomerId() + "/beneficiaries")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidAccReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(containsString("does not exist")));
    }

    @Test
    @DisplayName("Bill payment should deduct balance, create bill payment record, and write central transaction")
    void testSuccessfulBillPayment() throws Exception {
        Account account = accountRepository.findByAccountNumber("ACC-2001").orElseThrow();
        BigDecimal initialBalance = account.getBalance();
        BigDecimal paymentAmount = new BigDecimal("75.50");

        BillPaymentRequest req = new BillPaymentRequest("ACC-2001", "ELECTRICITY_01", paymentAmount);

        mockMvc.perform(post("/api/bills/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").value(startsWith("BILL-")))
                .andExpect(jsonPath("$.accountNumber").value("ACC-2001"))
                .andExpect(jsonPath("$.billerId").value("ELECTRICITY_01"))
                .andExpect(jsonPath("$.amount").value(75.50))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.transactionId").value(startsWith("TXN-")));

        // Verify balance was deducted
        Account updatedAccount = accountRepository.findByAccountNumber("ACC-2001").orElseThrow();
        assertEquals(initialBalance.subtract(paymentAmount), updatedAccount.getBalance());

        // Verify payment appears in central transactions history
        mockMvc.perform(get("/api/accounts/ACC-2001/transactions")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("BILL_PAYMENT"))
                .andExpect(jsonPath("$[0].destinationAccount").value("ELECTRICITY_01"));
    }

    @Test
    @DisplayName("Bill payment should reject insufficient funds and rollback balance")
    void testBillPaymentInsufficientFundsRollback() throws Exception {
        Account account = accountRepository.findByAccountNumber("ACC-1002").orElseThrow();
        BigDecimal initialBalance = account.getBalance();

        BillPaymentRequest excessiveReq = new BillPaymentRequest("ACC-1002", "WATER_02", new BigDecimal("9999999.00"));

        mockMvc.perform(post("/api/bills/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(excessiveReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Insufficient funds")));

        // Verify balance was not changed (rollback)
        Account unchanged = accountRepository.findByAccountNumber("ACC-1002").orElseThrow();
        assertEquals(initialBalance, unchanged.getBalance(), "Balance must remain unchanged on failed payment");
    }

    @Test
    @DisplayName("Bill payment should reject negative or zero amount")
    void testBillPaymentInvalidAmount() throws Exception {
        BillPaymentRequest negativeReq = new BillPaymentRequest("ACC-1001", "WATER_02", new BigDecimal("-25.00"));

        mockMvc.perform(post("/api/bills/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(negativeReq)))
                .andExpect(status().isBadRequest());
    }
}
