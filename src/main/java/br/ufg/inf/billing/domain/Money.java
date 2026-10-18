package br.ufg.inf.billing.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Value Object imutável para valores monetários em BRL.
 *
 * As operações preservam a precisão total; o arredondamento para centavos
 * (HALF_UP) acontece apenas em {@link #rounded()}, no fechamento da fatura.
 */
public record Money(BigDecimal amount) implements Comparable<Money> {

    public static final Money ZERO = new Money(BigDecimal.ZERO);
    private static final int CENTS_SCALE = 2;

    public Money {
        Objects.requireNonNull(amount, "amount");
    }

    public static Money of(String amount) {
        return new Money(new BigDecimal(amount));
    }

    public Money plus(Money other) {
        return new Money(amount.add(other.amount));
    }

    public Money minus(Money other) {
        return new Money(amount.subtract(other.amount));
    }

    public Money times(long factor) {
        return new Money(amount.multiply(BigDecimal.valueOf(factor)));
    }

    public Money times(BigDecimal factor) {
        return new Money(amount.multiply(factor));
    }

    /** Aplica um desconto percentual. Ex.: {@code discount(new BigDecimal("0.15"))} = 15% off. */
    public Money discount(BigDecimal rate) {
        return minus(times(rate));
    }

    /** Valor nunca negativo: devolve zero quando o resultado ficaria abaixo de zero. */
    public Money atLeastZero() {
        return isNegative() ? ZERO : this;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public Money rounded() {
        return new Money(amount.setScale(CENTS_SCALE, RoundingMode.HALF_UP));
    }

    @Override
    public int compareTo(Money other) {
        return amount.compareTo(other.amount);
    }

    /** Igualdade por valor numérico: 10.0 e 10.00 são o mesmo dinheiro. */
    @Override
    public boolean equals(Object o) {
        return o instanceof Money other && amount.compareTo(other.amount) == 0;
    }

    @Override
    public int hashCode() {
        return amount.stripTrailingZeros().hashCode();
    }

    @Override
    public String toString() {
        return rounded().amount.toPlainString();
    }
}
