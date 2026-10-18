package br.ufg.inf.billing.domain;

/** Planos comercializados: preço base, assentos incluídos e preço do assento adicional. */
public enum Plan {
    BASIC(Money.of("29.90"), 1, Money.of("9.90")),
    PRO(Money.of("99.90"), 5, Money.of("15.00")),
    ENTERPRISE(Money.of("499.90"), 20, Money.of("10.00"));

    private final Money basePrice;
    private final int includedSeats;
    private final Money extraSeatPrice;

    Plan(Money basePrice, int includedSeats, Money extraSeatPrice) {
        this.basePrice = basePrice;
        this.includedSeats = includedSeats;
        this.extraSeatPrice = extraSeatPrice;
    }

    public Money basePrice() {
        return basePrice;
    }

    public int includedSeats() {
        return includedSeats;
    }

    public Money extraSeatPrice() {
        return extraSeatPrice;
    }
}
