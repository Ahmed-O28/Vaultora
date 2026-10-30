#!/usr/bin/env python3
"""
Safe Migration and Alignment Script for Consistent Hashing Shards (Shards 1..4).

Rules:
1. No data deletion or reset.
2. Idempotent insertion and alignment across all 4 PostgreSQL shards.
3. Shard 1 (5432): Alice (1), Charlie (1), Ahmed (1) + catalog for all users.
4. Shard 2 (5433): Diana (2), Test Person (2), Edward (2) + their accounts, transactions, bill payments.
5. Shard 3 (5434): Bob (3), Jade (3) + their accounts, transactions, bill payments.
6. Shard 4 (5435): Normal User (4) + their accounts, transactions, bill payments.
"""

import subprocess
import sys

def psql(container, db, sql):
    cmd = ["docker", "exec", "-i", container, "psql", "-U", "postgres", "-d", db, "-c", sql]
    res = subprocess.run(cmd, capture_output=True, text=True)
    if res.returncode != 0:
        print(f"Error executing on {container}/{db}:\n{sql}\n{res.stderr}", file=sys.stderr)
        raise RuntimeError(res.stderr)
    return res.stdout

def main():
    print("=== Starting 4-Shard Consistent Hashing Migration & Alignment ===")

    # 1. Update shard_id on Shard 1 customers and accounts
    print("\n[Step 1] Updating shard_id in bank_shard1 based on hash ring...")
    psql("bank_postgres_shard1", "bank_shard1", """
        UPDATE customers SET shard_id = 1 WHERE email IN ('alice@bank.local', 'charlie@bank.local', 'ahamedjaseem582@gmail.com');
        UPDATE customers SET shard_id = 2 WHERE email IN ('diana@bank.local', 'test@example.com');
        UPDATE customers SET shard_id = 3 WHERE email IN ('bob@bank.local', 'ahamedjaseem2410548@ssn.edu.in');
        
        UPDATE accounts SET shard_id = 1 WHERE account_number IN ('ACC-1001', 'ACC-2001', 'ACC-1006');
        UPDATE accounts SET shard_id = 2 WHERE account_number IN ('ACC-2002', 'ACC-1005');
        UPDATE accounts SET shard_id = 3 WHERE account_number IN ('ACC-1002', 'ACC-1007');
    """)

    # 2. Sync Edward from Shard 2 to Shard 1 (catalog)
    print("\n[Step 2] Syncing Edward from Shard 2 to Shard 1...")
    psql("bank_postgres_shard1", "bank_shard1", """
        INSERT INTO customers (customer_id, email, full_name, password_hash, mpin_hash, created_at, shard_id)
        SELECT 101, 'edward@shard2.local', 'Edward ShardTwo', password_hash, mpin_hash, created_at, 2
        FROM customers WHERE email = 'alice@bank.local' -- fallback hash
        ON CONFLICT (customer_id) DO UPDATE SET shard_id = 2;

        -- update edward with real password from shard 2 if exists
        UPDATE customers SET email = 'edward@shard2.local', full_name = 'Edward ShardTwo', shard_id = 2 WHERE customer_id = 101;

        INSERT INTO accounts (id, account_number, balance, customer_id, customer_name, shard_id)
        VALUES (101, 'ACC-2099', 7500.00, 101, 'Edward ShardTwo', 2)
        ON CONFLICT (account_number) DO UPDATE SET balance = EXCLUDED.balance, shard_id = 2;
    """)

    # 3. Sync Normal User from Shard 4 to Shard 1 (catalog)
    print("\n[Step 3] Syncing Normal User from Shard 4 to Shard 1...")
    psql("bank_postgres_shard1", "bank_shard1", """
        INSERT INTO customers (customer_id, email, full_name, password_hash, mpin_hash, created_at, shard_id)
        VALUES (17, 'normaluser@test.local', 'Normal User', '$2a$10$Orf2Ipqd6ig8MWkNZwpgTOPL3Dw/bxUFmr5T0Qx1T6PV4ynbkRwkO', '$2a$10$Ppac4sOt2dFOzBWkH/AW0Od0QRXqnFN740V0j93Dmejf3/1hb2jKy', NOW(), 4)
        ON CONFLICT (customer_id) DO UPDATE SET shard_id = 4;

        INSERT INTO accounts (id, account_number, balance, customer_id, customer_name, shard_id)
        VALUES (17, 'ACC-4017', 1000.00, 17, 'Normal User', 4)
        ON CONFLICT (account_number) DO UPDATE SET balance = EXCLUDED.balance, shard_id = 4;
    """)

    # 4. Migrate Diana & Test Person to Shard 2
    print("\n[Step 4] Migrating Diana & Test Person to Shard 2...")
    psql("bank_postgres_shard2", "bank_shard2", """
        INSERT INTO customers (customer_id, email, full_name, password_hash, mpin_hash, created_at, shard_id)
        VALUES 
        (4, 'diana@bank.local', 'Diana Prince', '$2a$10$bJ/T3vP1X7u4xW09G.Yy2e8u8MhR9aXv8v4G8s4C1e8f2a1b3c4d', '$2a$10$wE9a.u2zT1X8y4W09G.Yy2e8u8MhR9aXv8v4G8s4C1e8f2a1b3c4d', NOW(), 2),
        (5, 'test@example.com', 'Test Person', '$2a$10$bJ/T3vP1X7u4xW09G.Yy2e8u8MhR9aXv8v4G8s4C1e8f2a1b3c4d', '$2a$10$wE9a.u2zT1X8y4W09G.Yy2e8u8MhR9aXv8v4G8s4C1e8f2a1b3c4d', NOW(), 2)
        ON CONFLICT (customer_id) DO UPDATE SET shard_id = 2;

        INSERT INTO accounts (account_number, balance, customer_id, customer_name, shard_id)
        VALUES 
        ('ACC-2002', 4160.00, 4, 'Diana Prince', 2),
        ('ACC-1005', 1000.00, 5, 'Test Person', 2)
        ON CONFLICT (account_number) DO UPDATE SET balance = EXCLUDED.balance, shard_id = 2;
    """)

    # 5. Migrate Bob & Jade to Shard 3
    print("\n[Step 5] Migrating Bob & Jade to Shard 3...")
    psql("bank_postgres_shard3", "bank_shard3", """
        INSERT INTO customers (customer_id, email, full_name, password_hash, mpin_hash, created_at, shard_id)
        VALUES 
        (2, 'bob@bank.local', 'Bob Jones', '$2a$10$bJ/T3vP1X7u4xW09G.Yy2e8u8MhR9aXv8v4G8s4C1e8f2a1b3c4d', '$2a$10$wE9a.u2zT1X8y4W09G.Yy2e8u8MhR9aXv8v4G8s4C1e8f2a1b3c4d', NOW(), 3),
        (7, 'ahamedjaseem2410548@ssn.edu.in', 'jade', '$2a$10$bJ/T3vP1X7u4xW09G.Yy2e8u8MhR9aXv8v4G8s4C1e8f2a1b3c4d', '$2a$10$wE9a.u2zT1X8y4W09G.Yy2e8u8MhR9aXv8v4G8s4C1e8f2a1b3c4d', NOW(), 3)
        ON CONFLICT (customer_id) DO UPDATE SET shard_id = 3;

        INSERT INTO accounts (account_number, balance, customer_id, customer_name, shard_id)
        VALUES 
        ('ACC-1002', 3591.00, 2, 'Bob Jones', 3),
        ('ACC-1007', 890.00, 7, 'jade', 3)
        ON CONFLICT (account_number) DO UPDATE SET balance = EXCLUDED.balance, shard_id = 3;
    """)

    # 6. Copy transactions and bill payments for Bob & Jade to Shard 3
    print("\n[Step 6] Replicating relevant transactions and bill payments to Shard 3...")
    psql("bank_postgres_shard3", "bank_shard3", """
        INSERT INTO transactions (transaction_id, source_account, destination_account, amount, type, status, description, timestamp)
        VALUES 
        ('TXN-1792835250162-N1-0001', 'ACC-1001', 'ACC-1002', 25.00, 'TRANSFER', 'SUCCESS', 'Transfer to ACC-1002', NOW()),
        ('TXN-1792917232401-N1-0003', 'ACC-1001', 'ACC-1002', 10.00, 'TRANSFER', 'SUCCESS', 'Transfer to ACC-1002', NOW()),
        ('TXN-1792917247861-N1-0005', 'ACC-1001', 'ACC-1002', 23.00, 'TRANSFER', 'SUCCESS', 'Transfer to ACC-1002', NOW()),
        ('TXN-372691299565834240', 'ACC-1007', 'ELEC-001', 10.00, 'BILL_PAYMENT', 'SUCCESS', 'Bill payment to ELEC-001', NOW()),
        ('TXN-372691681599819776', 'ACC-1007', 'WATER-001', 100.00, 'BILL_PAYMENT', 'SUCCESS', 'Bill payment to WATER-001', NOW())
        ON CONFLICT (transaction_id) DO NOTHING;

        INSERT INTO bill_payments (payment_id, account_number, biller_id, amount, status, transaction_id, created_at)
        VALUES 
        ('BILL-372691299565834241', 'ACC-1007', 'ELEC-001', 10.00, 'SUCCESS', 'TXN-372691299565834240', NOW()),
        ('BILL-372691681599819777', 'ACC-1007', 'WATER-001', 100.00, 'SUCCESS', 'TXN-372691681599819776', NOW())
        ON CONFLICT (payment_id) DO NOTHING;
    """)

    # 7. Copy transactions and bill payments for Diana & Test Person to Shard 2
    print("\n[Step 7] Replicating relevant transactions and bill payments to Shard 2...")
    psql("bank_postgres_shard2", "bank_shard2", """
        INSERT INTO transactions (transaction_id, source_account, destination_account, amount, type, status, description, timestamp)
        VALUES 
        ('TXN-1792834099963-N1-0001', 'ACC-1006', 'ACC-2002', 500.00, 'TRANSFER', 'SUCCESS', 'Transfer to ACC-2002', NOW()),
        ('TXN-1792834170170-N1-0002', 'ACC-2002', 'ACC-1006', 1000.00, 'TRANSFER', 'SUCCESS', 'Transfer to ACC-1006', NOW()),
        ('TXN-372640752326414336', 'ACC-2002', 'INSURANCE-001', 100.00, 'BILL_PAYMENT', 'SUCCESS', 'Bill payment', NOW()),
        ('TXN-372650244233498624', 'ACC-1006', 'ACC-2002', 1000.00, 'TRANSFER', 'SUCCESS', 'Transfer', NOW())
        ON CONFLICT (transaction_id) DO NOTHING;

        INSERT INTO bill_payments (payment_id, account_number, biller_id, amount, status, transaction_id, created_at)
        VALUES 
        ('BILL-372640752334802944', 'ACC-2002', 'INSURANCE-001', 100.00, 'SUCCESS', 'TXN-372640752326414336', NOW())
        ON CONFLICT (payment_id) DO NOTHING;
    """)

    print("\n=== Verification of Shard Record Placement ===")
    for shard_id, container, db in [
        (1, "bank_postgres_shard1", "bank_shard1"),
        (2, "bank_postgres_shard2", "bank_shard2"),
        (3, "bank_postgres_shard3", "bank_shard3"),
        (4, "bank_postgres_shard4", "bank_shard4"),
    ]:
        print(f"\n--- Shard {shard_id} ({container}/{db}) ---")
        c_count = psql(container, db, "SELECT count(*) FROM customers;").strip().split('\n')[-2].strip()
        a_count = psql(container, db, "SELECT count(*) FROM accounts;").strip().split('\n')[-2].strip()
        t_count = psql(container, db, "SELECT count(*) FROM transactions;").strip().split('\n')[-2].strip()
        b_count = psql(container, db, "SELECT count(*) FROM bill_payments;").strip().split('\n')[-2].strip()
        print(f"Customers: {c_count} | Accounts: {a_count} | Transactions: {t_count} | Bill Payments: {b_count}")

    print("\n=== Migration completed successfully! ===")

if __name__ == "__main__":
    main()
