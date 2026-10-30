package com.banking.config;

import com.banking.model.Account;
import com.banking.model.Beneficiary;
import com.banking.model.Customer;
import com.banking.repository.AccountRepository;
import com.banking.repository.BeneficiaryRepository;
import com.banking.repository.CustomerRepository;
import com.banking.model.Admin;
import com.banking.repository.AdminRepository;
import com.banking.systemdesign.sharding.ConsistentHashRouter;
import com.banking.systemdesign.sharding.ShardConnectionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;

@Configuration
public class DataInitializer {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Bean
    public CommandLineRunner initDemoData(CustomerRepository customerRepository,
                                         AccountRepository accountRepository,
                                         BeneficiaryRepository beneficiaryRepository,
                                         AdminRepository adminRepository,
                                         @Autowired(required = false) com.banking.repository.BillerRepository billerRepository,
                                         @Autowired(required = false) ConsistentHashRouter consistentHashRouter,
                                         @Autowired(required = false) ShardConnectionManager shardConnectionManager) {
        return args -> {
            // Seed initial Billers if empty
            if (billerRepository != null && billerRepository.count() == 0) {
                billerRepository.saveAll(List.of(
                        new com.banking.model.Biller("ELEC-001", "City Electric Co.", "Electricity", "ACTIVE"),
                        new com.banking.model.Biller("WATER-001", "Metro Water Authority", "Water", "ACTIVE"),
                        new com.banking.model.Biller("GAS-001", "National Gas Ltd.", "Gas", "ACTIVE"),
                        new com.banking.model.Biller("INTERNET-001", "Fiber Internet Inc.", "Internet", "INACTIVE"),
                        new com.banking.model.Biller("INSURANCE-001", "SafeGuard Insurance", "Insurance", "ACTIVE"),
                        new com.banking.model.Biller("PHONE-001", "Mobile Network Corp.", "Mobile", "ACTIVE"),
                        new com.banking.model.Biller("TAX-001", "Rates & Taxes Office", "Government", "ACTIVE")
                ));
            }

            // Seed initial Admin account if empty
            if (adminRepository.count() == 0) {
                String adminPasswordHash = passwordEncoder.encode("admin123");
                Admin initialAdmin = new Admin("admin", "admin@bank.local", adminPasswordHash, "ADMIN");
                adminRepository.save(initialAdmin);
            }
            // Seed Customers if empty
            if (customerRepository.count() == 0) {
                String defaultPasswordHash = passwordEncoder.encode("password123");
                String defaultMpinHash = passwordEncoder.encode("1234");

                Customer alice = customerRepository.save(new Customer("Alice Smith", "alice@bank.local", defaultPasswordHash, defaultMpinHash));
                Customer bob = customerRepository.save(new Customer("Bob Jones", "bob@bank.local", defaultPasswordHash, defaultMpinHash));
                Customer charlie = customerRepository.save(new Customer("Charlie Brown", "charlie@bank.local", defaultPasswordHash, defaultMpinHash));
                Customer diana = customerRepository.save(new Customer("Diana Prince", "diana@bank.local", defaultPasswordHash, defaultMpinHash));

                // Seed Accounts linked to Customers
                if (accountRepository.count() == 0) {
                    List<Account> demoAccounts = List.of(
                            new Account("ACC-1001", "Alice Smith", new BigDecimal("5000.00"), 1, alice.getCustomerId()),
                            new Account("ACC-1002", "Bob Jones", new BigDecimal("3500.00"), 1, bob.getCustomerId()),
                            new Account("ACC-2001", "Charlie Brown", new BigDecimal("10000.00"), 2, charlie.getCustomerId()),
                            new Account("ACC-2002", "Diana Prince", new BigDecimal("2500.00"), 2, diana.getCustomerId())
                    );
                    accountRepository.saveAll(demoAccounts);
                }

                // Seed initial Beneficiaries (Alice saves Bob, Bob saves Alice)
                if (beneficiaryRepository.count() == 0) {
                    beneficiaryRepository.save(new Beneficiary(alice.getCustomerId(), "ACC-1002", "Bob (Roommate)"));
                    beneficiaryRepository.save(new Beneficiary(bob.getCustomerId(), "ACC-1001", "Alice"));
                }
            } else {
                // Ensure all existing customers have MPIN and deterministic shardId
                String defaultMpinHash = passwordEncoder.encode("1234");
                customerRepository.findAll().forEach(customer -> {
                    boolean modified = false;
                    if (customer.getMpinHash() == null) {
                        customer.setMpinHash(defaultMpinHash);
                        modified = true;
                    }
                    if (customer.getShardId() == null && consistentHashRouter != null) {
                        customer.setShardId(consistentHashRouter.getShard(customer.getEmail()));
                        modified = true;
                    }
                    if (modified) {
                        customerRepository.save(customer);
                    }
                });
            }
        };
    }
}
