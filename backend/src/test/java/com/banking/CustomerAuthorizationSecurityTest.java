package com.banking;

import com.banking.dto.BeneficiaryRequest;
import com.banking.dto.LoginRequest;
import com.banking.model.Customer;
import com.banking.repository.CustomerRepository;
import com.banking.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CustomerAuthorizationSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private JwtService jwtService;

    @Test
    @DisplayName("Unauthenticated request to customer accounts must return HTTP 401 Unauthorized")
    void shouldRejectUnauthenticatedAccountAccess() throws Exception {
        mockMvc.perform(get("/api/customers/1/accounts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(containsString("Authentication required")));
    }

    @Test
    @DisplayName("Unauthenticated request to customer beneficiaries must return HTTP 401 Unauthorized")
    void shouldRejectUnauthenticatedBeneficiaryAccess() throws Exception {
        mockMvc.perform(get("/api/customers/1/beneficiaries")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(containsString("Authentication required")));
    }

    @Test
    @DisplayName("Invalid/forged token must return HTTP 401 Unauthorized")
    void shouldRejectInvalidToken() throws Exception {
        mockMvc.perform(get("/api/customers/1/accounts")
                        .header("Authorization", "Bearer invalid.jwt.token.string")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(containsString("Invalid or expired authentication token")));
    }

    @Test
    @DisplayName("Customer A must be forbidden (HTTP 403) from accessing Customer B's accounts")
    void shouldRejectCrossCustomerAccountAccess() throws Exception {
        Customer alice = customerRepository.findByEmail("alice@bank.local").orElseThrow();
        Customer bob = customerRepository.findByEmail("bob@bank.local").orElseThrow();

        // Generate token for Alice
        String aliceToken = jwtService.generateToken(alice.getCustomerId(), alice.getEmail());

        // Alice attempts to view Bob's accounts
        mockMvc.perform(get("/api/customers/" + bob.getCustomerId() + "/accounts")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(containsString("Access denied: You do not have permission")));
    }

    @Test
    @DisplayName("Customer A must be forbidden (HTTP 403) from listing Customer B's beneficiaries")
    void shouldRejectCrossCustomerBeneficiaryListing() throws Exception {
        Customer alice = customerRepository.findByEmail("alice@bank.local").orElseThrow();
        Customer bob = customerRepository.findByEmail("bob@bank.local").orElseThrow();

        String aliceToken = jwtService.generateToken(alice.getCustomerId(), alice.getEmail());

        // Alice attempts to list Bob's beneficiaries
        mockMvc.perform(get("/api/customers/" + bob.getCustomerId() + "/beneficiaries")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(containsString("Access denied: You do not have permission")));
    }

    @Test
    @DisplayName("Customer A must be forbidden (HTTP 403) from creating beneficiaries for Customer B")
    void shouldRejectCrossCustomerBeneficiaryCreation() throws Exception {
        Customer alice = customerRepository.findByEmail("alice@bank.local").orElseThrow();
        Customer bob = customerRepository.findByEmail("bob@bank.local").orElseThrow();

        String aliceToken = jwtService.generateToken(alice.getCustomerId(), alice.getEmail());

        BeneficiaryRequest req = new BeneficiaryRequest("ACC-2001", "Charlie");

        // Alice attempts to add a beneficiary into Bob's profile
        mockMvc.perform(post("/api/customers/" + bob.getCustomerId() + "/beneficiaries")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(containsString("Access denied: You do not have permission")));
    }

    @Test
    @DisplayName("Authenticated customer must be able to access their own accounts and beneficiaries")
    void shouldAllowAccessToOwnResources() throws Exception {
        Customer alice = customerRepository.findByEmail("alice@bank.local").orElseThrow();
        String aliceToken = jwtService.generateToken(alice.getCustomerId(), alice.getEmail());

        // Alice accesses her own accounts
        mockMvc.perform(get("/api/customers/" + alice.getCustomerId() + "/accounts")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].accountNumber").value("ACC-1001"));

        // Alice accesses her own beneficiaries
        mockMvc.perform(get("/api/customers/" + alice.getCustomerId() + "/beneficiaries")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Login endpoint must return valid JWT token in CustomerResponse")
    void shouldReturnTokenOnLogin() throws Exception {
        LoginRequest login = new LoginRequest("bob@bank.local", "password123");

        mockMvc.perform(post("/api/customers/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").isNumber())
                .andExpect(jsonPath("$.email").value("bob@bank.local"))
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("Verify valid MPIN returns verified status and balance")
    void shouldVerifyValidMpin() throws Exception {
        Customer alice = customerRepository.findByEmail("alice@bank.local").orElseThrow();
        String aliceToken = jwtService.generateToken(alice.getCustomerId(), alice.getEmail());

        com.banking.dto.MpinRequest req = new com.banking.dto.MpinRequest("1234", "ACC-1001");
        mockMvc.perform(post("/api/customers/" + alice.getCustomerId() + "/verify-mpin")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.balance").isNumber());
    }

    @Test
    @DisplayName("Verify invalid MPIN returns HTTP 401 Unauthorized")
    void shouldRejectInvalidMpin() throws Exception {
        Customer alice = customerRepository.findByEmail("alice@bank.local").orElseThrow();
        String aliceToken = jwtService.generateToken(alice.getCustomerId(), alice.getEmail());

        com.banking.dto.MpinRequest req = new com.banking.dto.MpinRequest("9999", "ACC-1001");
        mockMvc.perform(post("/api/customers/" + alice.getCustomerId() + "/verify-mpin")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(containsString("Incorrect MPIN")));
    }
}
