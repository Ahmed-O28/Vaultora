package com.banking.dto;

import jakarta.validation.constraints.NotBlank;

public class BeneficiaryRequest {

    @NotBlank(message = "Beneficiary account number is required")
    private String beneficiaryAccountNumber;

    private String nickname;

    public BeneficiaryRequest() {
    }

    public BeneficiaryRequest(String beneficiaryAccountNumber, String nickname) {
        this.beneficiaryAccountNumber = beneficiaryAccountNumber;
        this.nickname = nickname;
    }

    public String getBeneficiaryAccountNumber() {
        return beneficiaryAccountNumber;
    }

    public void setBeneficiaryAccountNumber(String beneficiaryAccountNumber) {
        this.beneficiaryAccountNumber = beneficiaryAccountNumber;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
}
