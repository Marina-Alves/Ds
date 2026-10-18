package br.ufg.inf.billing.domain;

import java.time.LocalDate;
import java.util.Objects;

public record Customer(String id, String name, LocalDate signupDate, boolean active) {

    public Customer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(signupDate, "signupDate");
    }
}
