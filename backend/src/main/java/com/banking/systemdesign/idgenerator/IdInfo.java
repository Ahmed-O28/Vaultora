package com.banking.systemdesign.idgenerator;

import java.time.Instant;

/**
 * Decoded view of an identifier produced by {@link DistributedSnowflakeIdGenerator}.
 *
 * @param id        the full identifier, e.g. "TXN-7254849302817280001"
 * @param prefix    domain prefix, e.g. "TXN"
 * @param numericId the 64-bit Snowflake value
 * @param createdAt the moment the identifier was minted (millisecond precision)
 * @param nodeId    the node (0-1023) that minted the identifier
 * @param sequence  the per-millisecond sequence number (0-4095)
 */
public record IdInfo(String id, String prefix, long numericId, Instant createdAt, int nodeId, int sequence) {
}
