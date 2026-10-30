package com.banking;

import com.banking.model.Biller;
import com.banking.repository.BillerRepository;
import com.banking.systemdesign.idgenerator.DistributedSnowflakeIdGenerator;
import com.banking.systemdesign.idgenerator.UniqueIdGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IdGeneratorAndBillerApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UniqueIdGenerator uniqueIdGenerator;

    @Autowired
    private BillerRepository billerRepository;

    @Test
    @DisplayName("Spring injects the Snowflake generator wherever UniqueIdGenerator is required")
    void snowflakeGeneratorIsPrimary() {
        assertInstanceOf(DistributedSnowflakeIdGenerator.class, uniqueIdGenerator);
    }

    @Test
    @DisplayName("GET /api/system/ids/generate returns prefixed unique ids")
    void generateEndpoint() throws Exception {
        mockMvc.perform(get("/api/system/ids/generate").param("prefix", "pay").param("count", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prefix").value("PAY"))
                .andExpect(jsonPath("$.ids", hasSize(3)))
                .andExpect(jsonPath("$.ids[0]").value(startsWith("PAY-")));

        mockMvc.perform(get("/api/system/ids/generate").param("count", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/system/ids/parse decodes an id and rejects malformed ones")
    void parseEndpoint() throws Exception {
        String id = uniqueIdGenerator.generateId("TXN");
        mockMvc.perform(get("/api/system/ids/parse").param("id", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prefix").value("TXN"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        mockMvc.perform(get("/api/system/ids/parse").param("id", "not-an-id"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/billers returns biller directory with status")
    void billersEndpoint() throws Exception {
        mockMvc.perform(get("/api/billers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billers", hasSize(greaterThanOrEqualTo(6))))
                .andExpect(jsonPath("$.billers[0].billerId").isNotEmpty())
                .andExpect(jsonPath("$.billers[0].status").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/billers/active returns only active billers")
    void activeBillersEndpoint() throws Exception {
        mockMvc.perform(get("/api/billers/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].status", everyItem(equalToIgnoringCase("ACTIVE"))));
    }

    @Autowired
    private com.banking.service.JwtService jwtService;

    @Test
    @DisplayName("Admin can activate and deactivate billers, persisting to database")
    void adminBillerManagementEndpoint() throws Exception {
        String adminToken = "Bearer " + jwtService.generateToken(1L, "admin@bank.local", "ADMIN");

        // Deactivate ELEC-001
        mockMvc.perform(post("/api/admin/billers/ELEC-001/status")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billerId").value("ELEC-001"))
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        Biller biller = billerRepository.findById("ELEC-001").orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("INACTIVE", biller.getStatus());

        // Reactivate ELEC-001
        mockMvc.perform(post("/api/admin/billers/ELEC-001/status")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billerId").value("ELEC-001"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        biller = billerRepository.findById("ELEC-001").orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("ACTIVE", biller.getStatus());
    }

    @Test
    @DisplayName("Bill payment to an INACTIVE biller is rejected by the backend API")
    void cannotPayInactiveBiller() throws Exception {
        // Ensure INTERNET-001 is INACTIVE
        billerRepository.save(new Biller("INTERNET-001", "Fiber Internet Inc.", "Internet", "INACTIVE"));

        // Attempt bill payment from ACC-1001 to INTERNET-001
        mockMvc.perform(post("/api/bills/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountNumber\": \"ACC-1001\", \"billerId\": \"INTERNET-001\", \"amount\": 25.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("INACTIVE")));
    }
}
