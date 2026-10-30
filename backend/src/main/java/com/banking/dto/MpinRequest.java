package com.banking.dto;

import jakarta.validation.constraints.NotBlank;

public class MpinRequest {

    @NotBlank(message = "MPIN is required")
    private String mpin;

    private String accountNumber;

    public MpinRequest() {
    }

    public MpinRequest(String mpin) {
        this(mpin, null);
    }

    public MpinRequest(String mpin, String accountNumber) {
        this.mpin = mpin;
        this.accountNumber = accountNumber;
    }

    public String getMpin() {
        return mpin;
    }

    public void setMpin(String mpin) {
        this.mpin = mpin;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }
}
