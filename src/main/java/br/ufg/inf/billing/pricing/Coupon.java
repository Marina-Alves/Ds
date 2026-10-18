package br.ufg.inf.billing.pricing;

import br.ufg.inf.billing.domain.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Optional;

/** Cupons promocionais. Cada cupom conhece a própria regra (Replace Conditional with Polymorphism). */
public enum Coupon {

    /** 10% para clientes com até 30 dias de cadastro. */
    WELCOME10 {
        @Override
        Money apply(Money price, LocalDate signupDate, LocalDate today) {
            return ChronoUnit.DAYS.between(signupDate, today) <= 30 ? price.discount(new BigDecimal("0.10")) : price;
        }
    },

    /** 30% apenas em novembro. */
    BLACKFRIDAY {
        @Override
        Money apply(Money price, LocalDate signupDate, LocalDate today) {
            return today.getMonth() == Month.NOVEMBER ? price.discount(new BigDecimal("0.30")) : price;
        }
    },

    /** R$ 20,00 de desconto fixo, sem deixar o valor negativo. */
    FIXED20 {
        @Override
        Money apply(Money price, LocalDate signupDate, LocalDate today) {
            return price.minus(Money.of("20")).atLeastZero();
        }
    };

    abstract Money apply(Money price, LocalDate signupDate, LocalDate today);

    /** Código vazio ou desconhecido não aplica desconto, como no sistema legado. */
    public static Optional<Coupon> fromCode(String code) {
        return Arrays.stream(values()).filter(c -> c.name().equals(code)).findFirst();
    }
}
