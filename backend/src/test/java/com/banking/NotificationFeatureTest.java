package com.banking;

import com.banking.dto.BillPaymentRequest;
import com.banking.dto.TransferRequest;
import com.banking.model.Account;
import com.banking.model.Customer;
import com.banking.model.Notification;
import com.banking.repository.AccountRepository;
import com.banking.repository.CustomerRepository;
import com.banking.service.JwtService;
import com.banking.service.NotificationService;
import com.banking.service.TransactionNotificationFeed;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Uses its own freshly created customers and accounts so it cannot disturb the seeded demo data
 * that other tests rely on.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NotificationFeatureTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private TransactionNotificationFeed feed;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    private Customer newCustomer(String name, String email, String accountNumber) {
        Customer c = customerRepository.save(
                new Customer(name, email, encoder.encode("password123"), encoder.encode("1234")));
        accountRepository.save(new Account(accountNumber, name, new BigDecimal("500.00"), 1, c.getCustomerId()));
        return c;
    }

    private String bearer(Customer c) {
        return "Bearer " + jwtService.generateToken(c.getCustomerId(), c.getEmail());
    }

    @Test
    @DisplayName("A transfer pushes a debit alert to the sender and a credit alert to the receiver")
    void transferCreatesDebitAndCreditNotifications() throws Exception {
        Customer sender = newCustomer("Notify Sender", "ntf.sender@bank.local", "ACC-NTF-A1");
        Customer receiver = newCustomer("Notify Receiver", "ntf.receiver@bank.local", "ACC-NTF-B1");

        feed.pollOnce(); // make sure the feed is initialised before the transfer happens

        TransferRequest transfer = new TransferRequest("ACC-NTF-A1", "ACC-NTF-B1",
                new BigDecimal("25.00"), "Notification test", "1234");
        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transfer)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionId").value(matchesPattern("TXN-\\d{10,19}")));

        feed.pollOnce();

        mockMvc.perform(get("/api/customers/" + sender.getCustomerId() + "/notifications")
                        .header("Authorization", bearer(sender)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("TRANSFER_DEBIT"))
                .andExpect(jsonPath("$[0].seen").value(false))
                .andExpect(jsonPath("$[0].message").value(containsString("$25.00")));

        mockMvc.perform(get("/api/customers/" + receiver.getCustomerId() + "/notifications")
                        .header("Authorization", bearer(receiver)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("TRANSFER_CREDIT"));

        mockMvc.perform(get("/api/customers/" + receiver.getCustomerId() + "/notifications/unread-count")
                        .header("Authorization", bearer(receiver)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        mockMvc.perform(post("/api/customers/" + receiver.getCustomerId() + "/notifications/read-all")
                        .header("Authorization", bearer(receiver)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(1));

        mockMvc.perform(get("/api/customers/" + receiver.getCustomerId() + "/notifications/unread-count")
                        .header("Authorization", bearer(receiver)))
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    @DisplayName("A bill payment pushes a receipt notification to the paying customer")
    void billPaymentCreatesNotification() throws Exception {
        Customer payer = newCustomer("Notify Payer", "ntf.payer@bank.local", "ACC-NTF-P1");

        feed.pollOnce();

        mockMvc.perform(post("/api/bills/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new BillPaymentRequest("ACC-NTF-P1", "ELEC-001", new BigDecimal("40.00")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").value(startsWith("BILL-")));

        feed.pollOnce();

        mockMvc.perform(get("/api/customers/" + payer.getCustomerId() + "/notifications")
                        .header("Authorization", bearer(payer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("BILL_PAYMENT"))
                .andExpect(jsonPath("$[0].title").value("Bill payment successful"))
                .andExpect(jsonPath("$[0].message").value(containsString("ELEC-001")));
    }

    @Test
    @DisplayName("Customers cannot read or modify each other's notifications")
    void notificationsAreIsolatedPerCustomer() throws Exception {
        Customer owner = newCustomer("Notify Owner", "ntf.owner@bank.local", "ACC-NTF-O1");
        Customer other = newCustomer("Notify Other", "ntf.other@bank.local", "ACC-NTF-O2");

        Notification n = notificationService.notifyCustomer(owner.getCustomerId(), "SYSTEM", "Hello", "Private", null);

        // no token -> 401
        mockMvc.perform(get("/api/customers/" + owner.getCustomerId() + "/notifications"))
                .andExpect(status().isUnauthorized());

        // someone else's token on the owner's path -> 403
        mockMvc.perform(get("/api/customers/" + owner.getCustomerId() + "/notifications")
                        .header("Authorization", bearer(other)))
                .andExpect(status().isForbidden());

        // marking another customer's notification id through your own path -> 404
        mockMvc.perform(post("/api/customers/" + other.getCustomerId() + "/notifications/" + n.getId() + "/read")
                        .header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());

        // the owner can mark it read
        mockMvc.perform(post("/api/customers/" + owner.getCustomerId() + "/notifications/" + n.getId() + "/read")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seen").value(true));
    }

    @Test
    @DisplayName("Test push endpoint stores a SYSTEM notification")
    void testPushEndpoint() throws Exception {
        Customer c = newCustomer("Notify Tester", "ntf.tester@bank.local", "ACC-NTF-T1");

        mockMvc.perform(post("/api/customers/" + c.getCustomerId() + "/notifications/test")
                        .header("Authorization", bearer(c)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SYSTEM"));

        mockMvc.perform(get("/api/customers/" + c.getCustomerId() + "/notifications")
                        .header("Authorization", bearer(c)))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("Opening the SSE stream registers a live push connection")
    void streamRegistersConnection() throws Exception {
        Customer c = newCustomer("Notify Streamer", "ntf.streamer@bank.local", "ACC-NTF-S1");

        mockMvc.perform(get("/api/customers/" + c.getCustomerId() + "/notifications/stream")
                        .header("Authorization", bearer(c))
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted());

        assertEquals(1, notificationService.activeConnections(c.getCustomerId()));
    }

    @Test
    @DisplayName("A transfer between a customer's own accounts creates exactly one notification, not duplicate debit+credit")
    void transferBetweenOwnAccountsCreatesSingleNotification() throws Exception {
        Customer c = customerRepository.save(
                new Customer("Self Sender", "ntf.self@bank.local", encoder.encode("password123"), encoder.encode("1234")));
        accountRepository.save(new Account("ACC-NTF-SELF-1", "Self Sender", new BigDecimal("500.00"), 1, c.getCustomerId()));
        accountRepository.save(new Account("ACC-NTF-SELF-2", "Self Sender", new BigDecimal("200.00"), 1, c.getCustomerId()));

        feed.pollOnce();

        TransferRequest transfer = new TransferRequest("ACC-NTF-SELF-1", "ACC-NTF-SELF-2",
                new BigDecimal("50.00"), "Internal self-transfer", "1234");
        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transfer)))
                .andExpect(status().isCreated());

        feed.pollOnce();

        // Must receive exactly 1 notification for the single transaction, not two
        mockMvc.perform(get("/api/customers/" + c.getCustomerId() + "/notifications")
                        .header("Authorization", bearer(c)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("TRANSFER"))
                .andExpect(jsonPath("$[0].title").value("Account transfer successful"));
    }

    @Test
    @DisplayName("Calling notifyCustomer multiple times with the same transaction referenceId prevents duplicates")
    void duplicateTransactionReferenceIdIsPrevented() throws Exception {
        Customer c = newCustomer("Dedup Customer", "ntf.dedup@bank.local", "ACC-NTF-D1");

        // First notification
        Notification n1 = notificationService.notifyCustomer(
                c.getCustomerId(), "BILL_PAYMENT", "Bill paid", "Payment of $30.00 processed", "TXN-TEST-DEDUP-999");

        // Second notification with identical transaction referenceId
        Notification n2 = notificationService.notifyCustomer(
                c.getCustomerId(), "BILL_PAYMENT", "Bill paid", "Payment of $30.00 processed", "TXN-TEST-DEDUP-999");

        // Both return the exact same Notification ID
        assertEquals(n1.getId(), n2.getId(), "Duplicate notification call must return existing notification without creating another");

        // Customer's notification list has exactly 1 notification
        mockMvc.perform(get("/api/customers/" + c.getCustomerId() + "/notifications")
                        .header("Authorization", bearer(c)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].referenceId").value("TXN-TEST-DEDUP-999"));
    }

    @Test
    @DisplayName("Rapid twin transactions with different transaction IDs within 5s window produce only one notification")
    void rapidTwinTransactionsProduceSingleNotification() throws Exception {
        Customer sender = newCustomer("Twin Sender", "ntf.twin.s@bank.local", "ACC-TWIN-S");
        Customer receiver = newCustomer("Twin Receiver", "ntf.twin.r@bank.local", "ACC-TWIN-R");

        feed.pollOnce();

        // Simulate two identical transfers created rapidly (twin submissions)
        TransferRequest t1 = new TransferRequest("ACC-TWIN-S", "ACC-TWIN-R", new BigDecimal("15.00"), "Twin 1", "1234");
        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(t1)))
                .andExpect(status().isCreated());

        TransferRequest t2 = new TransferRequest("ACC-TWIN-S", "ACC-TWIN-R", new BigDecimal("15.00"), "Twin 2", "1234");
        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(t2)))
                .andExpect(status().isCreated());

        feed.pollOnce();

        // Sender must have only 1 debit notification due to 5s debounce, not 2
        mockMvc.perform(get("/api/customers/" + sender.getCustomerId() + "/notifications")
                        .header("Authorization", bearer(sender)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("TRANSFER_DEBIT"));
    }
}
