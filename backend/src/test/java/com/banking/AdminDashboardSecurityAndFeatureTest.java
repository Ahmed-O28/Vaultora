package com.banking;

import com.banking.dto.AdminLoginRequest;
import com.banking.dto.RegisterRequest;
import com.banking.model.Customer;
import com.banking.repository.AdminRepository;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminDashboardSecurityAndFeatureTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private JwtService jwtService;

    @Test
    @DisplayName("Initial admin account must be seeded securely on startup")
    void shouldHaveInitialAdminSeeded() {
        assertTrue(adminRepository.existsByUsername("admin"), "Initial admin account should exist");
        var admin = adminRepository.findByUsername("admin").orElseThrow();
        assertEquals("ADMIN", admin.getRole());
        assertNotNull(admin.getPasswordHash());
    }

    @Test
    @DisplayName("Admin login with valid credentials should succeed and return JWT token")
    void shouldAllowAdminLoginWithValidCredentials() throws Exception {
        AdminLoginRequest request = new AdminLoginRequest("admin", "admin123");

        mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.username").value("admin"));
    }

    @Test
    @DisplayName("Admin login with invalid password should fail with HTTP 401")
    void shouldRejectAdminLoginWithWrongPassword() throws Exception {
        AdminLoginRequest request = new AdminLoginRequest("admin", "wrongpassword");

        mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Unauthenticated request to admin APIs must return HTTP 401")
    void shouldRejectUnauthenticatedAdminApiAccess() throws Exception {
        mockMvc.perform(get("/api/admin/customers"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/transactions"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Customer token accessing admin API must be rejected with HTTP 403 Forbidden")
    void shouldRejectCustomerTokenAccessToAdminApi() throws Exception {
        // Generate customer token with role CUSTOMER
        Customer customer = customerRepository.findAll().get(0);
        String customerToken = jwtService.generateToken(customer.getCustomerId(), customer.getEmail(), "CUSTOMER");

        mockMvc.perform(get("/api/admin/customers")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(containsString("Admin role required")));
    }

    @Test
    @DisplayName("Admin token accessing admin customers API returns 200 and never exposes password/MPIN")
    void shouldReturnCustomersWithoutSensitiveFieldsForAdmin() throws Exception {
        String adminToken = jwtService.generateToken(1L, "admin@bank.local", "ADMIN");

        mockMvc.perform(get("/api/admin/customers")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(not(empty()))))
                .andExpect(jsonPath("$[0].customerId").exists())
                .andExpect(jsonPath("$[0].fullName").exists())
                .andExpect(jsonPath("$[0].email").exists())
                .andExpect(jsonPath("$[0].accountNumber").exists())
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[0].mpin").doesNotExist())
                .andExpect(jsonPath("$[0].mpinHash").doesNotExist());
    }

    @Test
    @DisplayName("Admin token accessing admin transactions API returns 200 with transaction records")
    void shouldReturnTransactionsForAdmin() throws Exception {
        String adminToken = jwtService.generateToken(1L, "admin@bank.local", "ADMIN");

        mockMvc.perform(get("/api/admin/transactions")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Admin token accessing admin stats returns aggregated metrics")
    void shouldReturnAdminStats() throws Exception {
        String adminToken = jwtService.generateToken(1L, "admin@bank.local", "ADMIN");

        mockMvc.perform(get("/api/admin/stats")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCustomers").isNumber())
                .andExpect(jsonPath("$.totalTransactions").isNumber());
    }

    @Test
    @DisplayName("Customer registration does not allow registering as admin")
    void customerRegistrationCannotCreateAdmin() throws Exception {
        RegisterRequest registerReq = new RegisterRequest();
        registerReq.setFullName("Normal User");
        registerReq.setEmail("normaluser@test.local");
        registerReq.setPassword("Password123!");
        registerReq.setMpin("9999");

        mockMvc.perform(post("/api/customers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        // Verify normaluser is NOT in admin repository
        assertFalse(adminRepository.existsByEmail("normaluser@test.local"));
    }
}
