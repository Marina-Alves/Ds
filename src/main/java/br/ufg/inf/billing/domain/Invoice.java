package br.ufg.inf.billing.domain;

import java.time.LocalDate;
import java.util.Objects;

public record Invoice(String customerId, Money amount, String transactionId, LocalDate issuedAt) {

    public Invoice {
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(issuedAt, "issuedAt");
    }
}
