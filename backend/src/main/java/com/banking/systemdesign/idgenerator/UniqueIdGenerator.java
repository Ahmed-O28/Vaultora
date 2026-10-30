package com.banking.systemdesign.idgenerator;

/**
 * Component 5: Unique ID Generator
 * 
 * Responsibility: Generate globally unique, roughly time-ordered 64-bit/alphanumeric IDs
 * for banking transactions and synthetic bill payments without centralized database locks.
 * 
 * Future Production Implementation:
 * Distributed Snowflake ID generator (epoch timestamp + datacenter ID + machine ID + sequence counter).
 */
public interface UniqueIdGenerator {

    /**
     * Generates a globally unique identifier with a given domain prefix.
     *
     * @param prefix domain prefix, e.g. "TXN", "PAY", "ACC"
     * @return unique identifier string
     */
    String generateId(String prefix);
}
