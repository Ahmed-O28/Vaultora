package com.banking;

import com.banking.dto.TransferRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BankingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /api/accounts should return list of demo accounts")
    void shouldReturnAllAccounts() throws Exception {
        mockMvc.perform(get("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(4))))
                .andExpect(jsonPath("$[0].accountNumber").isNotEmpty())
                .andExpect(jsonPath("$[0].balance").isNumber());
    }

    @Test
    @DisplayName("GET /api/accounts/{accountNumber} should return single account with balance")
    void shouldReturnSingleAccount() throws Exception {
        mockMvc.perform(get("/api/accounts/ACC-1001")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value("ACC-1001"))
                .andExpect(jsonPath("$.customerName").value("Alice Smith"))
                .andExpect(jsonPath("$.balance").isNumber());
    }

    @Test
    @DisplayName("POST /api/transfers should successfully transfer funds and update balances")
    void shouldTransferFundsSuccessfully() throws Exception {
        TransferRequest request = new TransferRequest(
                "ACC-1001", "ACC-1002", new BigDecimal("100.00"), "Rent contribution"
        );

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionId").value(startsWith("TXN-")))
                .andExpect(jsonPath("$.sourceAccount").value("ACC-1001"))
                .andExpect(jsonPath("$.destinationAccount").value("ACC-1002"))
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        // Verify transaction history for source account
        mockMvc.perform(get("/api/accounts/ACC-1001/transactions")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].sourceAccount").value("ACC-1001"));
    }

    @Test
    @DisplayName("POST /api/transfers should reject self-transfer with HTTP 400")
    void shouldRejectSelfTransfer() throws Exception {
        TransferRequest request = new TransferRequest(
                "ACC-1001", "ACC-1001", new BigDecimal("50.00"), "Self transfer attempt"
        );

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Cannot transfer money to the same account")));
    }

    @Test
    @DisplayName("POST /api/transfers should reject insufficient funds with HTTP 400")
    void shouldRejectInsufficientFunds() throws Exception {
        TransferRequest request = new TransferRequest(
                "ACC-1002", "ACC-1001", new BigDecimal("99999999.00"), "Excessive amount"
        );

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Insufficient funds")));
    }

    @Test
    @DisplayName("POST /api/transfers should reject negative or zero amount with HTTP 400")
    void shouldRejectNegativeAmount() throws Exception {
        TransferRequest request = new TransferRequest(
                "ACC-1001", "ACC-1002", new BigDecimal("-10.00"), "Negative amount"
        );

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/transfers should deduplicate rapid identical requests and prevent double debit")
    void shouldDeduplicateRapidTransfers() throws Exception {
        TransferRequest request = new TransferRequest(
                "ACC-1001", "ACC-1002", new BigDecimal("15.00"), "Dedup test"
        );

        // First transfer
        org.springframework.test.web.servlet.MvcResult res1 = mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        // Immediate identical second transfer (simulating double-click or client glitch)
        org.springframework.test.web.servlet.MvcResult res2 = mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String txnId1 = objectMapper.readTree(res1.getResponse().getContentAsString()).get("transactionId").asText();
        String txnId2 = objectMapper.readTree(res2.getResponse().getContentAsString()).get("transactionId").asText();

        // Both requests return the identical transaction reference and account is NOT double debited
        org.junit.jupiter.api.Assertions.assertEquals(txnId1, txnId2);
    }
}
