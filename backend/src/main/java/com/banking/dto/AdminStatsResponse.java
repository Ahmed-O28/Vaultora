package com.banking.dto;

import java.math.BigDecimal;

public class AdminStatsResponse {

    private long totalCustomers;
    private long totalAccounts;
    private long totalTransactions;
    private BigDecimal totalDepositVolume;
    private long shard1CustomerCount;
    private long shard2CustomerCount;
    private long shard3CustomerCount;
    private long shard4CustomerCount;
    private long shard1TransactionCount;
    private long shard2TransactionCount;
    private long shard3TransactionCount;
    private long shard4TransactionCount;

    public AdminStatsResponse() {
    }

    public AdminStatsResponse(long totalCustomers, long totalAccounts, long totalTransactions,
                              BigDecimal totalDepositVolume, long shard1CustomerCount, long shard2CustomerCount,
                              long shard1TransactionCount, long shard2TransactionCount) {
        this(totalCustomers, totalAccounts, totalTransactions, totalDepositVolume,
                shard1CustomerCount, shard2CustomerCount, 0, 0,
                shard1TransactionCount, shard2TransactionCount, 0, 0);
    }

    public AdminStatsResponse(long totalCustomers, long totalAccounts, long totalTransactions,
                              BigDecimal totalDepositVolume,
                              long shard1CustomerCount, long shard2CustomerCount, long shard3CustomerCount, long shard4CustomerCount,
                              long shard1TransactionCount, long shard2TransactionCount, long shard3TransactionCount, long shard4TransactionCount) {
        this.totalCustomers = totalCustomers;
        this.totalAccounts = totalAccounts;
        this.totalTransactions = totalTransactions;
        this.totalDepositVolume = totalDepositVolume;
        this.shard1CustomerCount = shard1CustomerCount;
        this.shard2CustomerCount = shard2CustomerCount;
        this.shard3CustomerCount = shard3CustomerCount;
        this.shard4CustomerCount = shard4CustomerCount;
        this.shard1TransactionCount = shard1TransactionCount;
        this.shard2TransactionCount = shard2TransactionCount;
        this.shard3TransactionCount = shard3TransactionCount;
        this.shard4TransactionCount = shard4TransactionCount;
    }

    public long getTotalCustomers() {
        return totalCustomers;
    }

    public void setTotalCustomers(long totalCustomers) {
        this.totalCustomers = totalCustomers;
    }

    public long getTotalAccounts() {
        return totalAccounts;
    }

    public void setTotalAccounts(long totalAccounts) {
        this.totalAccounts = totalAccounts;
    }

    public long getTotalTransactions() {
        return totalTransactions;
    }

    public void setTotalTransactions(long totalTransactions) {
        this.totalTransactions = totalTransactions;
    }

    public BigDecimal getTotalDepositVolume() {
        return totalDepositVolume;
    }

    public void setTotalDepositVolume(BigDecimal totalDepositVolume) {
        this.totalDepositVolume = totalDepositVolume;
    }

    public long getShard1CustomerCount() {
        return shard1CustomerCount;
    }

    public void setShard1CustomerCount(long shard1CustomerCount) {
        this.shard1CustomerCount = shard1CustomerCount;
    }

    public long getShard2CustomerCount() {
        return shard2CustomerCount;
    }

    public void setShard2CustomerCount(long shard2CustomerCount) {
        this.shard2CustomerCount = shard2CustomerCount;
    }

    public long getShard1TransactionCount() {
        return shard1TransactionCount;
    }

    public void setShard1TransactionCount(long shard1TransactionCount) {
        this.shard1TransactionCount = shard1TransactionCount;
    }

    public long getShard2TransactionCount() {
        return shard2TransactionCount;
    }

    public void setShard2TransactionCount(long shard2TransactionCount) {
        this.shard2TransactionCount = shard2TransactionCount;
    }

    public long getShard3CustomerCount() {
        return shard3CustomerCount;
    }

    public void setShard3CustomerCount(long shard3CustomerCount) {
        this.shard3CustomerCount = shard3CustomerCount;
    }

    public long getShard4CustomerCount() {
        return shard4CustomerCount;
    }

    public void setShard4CustomerCount(long shard4CustomerCount) {
        this.shard4CustomerCount = shard4CustomerCount;
    }

    public long getShard3TransactionCount() {
        return shard3TransactionCount;
    }

    public void setShard3TransactionCount(long shard3TransactionCount) {
        this.shard3TransactionCount = shard3TransactionCount;
    }

    public long getShard4TransactionCount() {
        return shard4TransactionCount;
    }

    public void setShard4TransactionCount(long shard4TransactionCount) {
        this.shard4TransactionCount = shard4TransactionCount;
    }
}
