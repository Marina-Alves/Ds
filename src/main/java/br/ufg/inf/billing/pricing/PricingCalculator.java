package br.ufg.inf.billing.pricing;

import br.ufg.inf.billing.BillingCommand;
import br.ufg.inf.billing.domain.BillingCycle;
import br.ufg.inf.billing.domain.Customer;
import br.ufg.inf.billing.domain.Money;
import br.ufg.inf.billing.domain.Plan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Regras de precificação, sem I/O nem relógio: a data de referência chega como argumento.
 * Ordem de aplicação: plano + assentos, ciclo anual, cupom, fidelidade, imposto, arredondamento.
 */
public class PricingCalculator {

    private static final int MONTHS_PER_YEAR = 12;
    private static final BigDecimal ANNUAL_DISCOUNT = new BigDecimal("0.15");
    private static final int LOYALTY_MIN_YEARS = 2;
    private static final BigDecimal LOYALTY_DISCOUNT = new BigDecimal("0.05");
    private static final String TAXED_COUNTRY = "BR";
    private static final BigDecimal BR_TAX_RATE = new BigDecimal("0.05");

    public Money calculate(BillingCommand command, Customer customer, LocalDate today) {
        return unroundedTotal(command, customer, today).rounded();
    }

    Money unroundedTotal(BillingCommand command, Customer customer, LocalDate today) {
        Money price = subscriptionPrice(command.plan(), command.seats());
        price = applyCycle(price, command.cycle());
        price = applyCoupon(price, command.couponCode(), customer.signupDate(), today);
        price = applyLoyalty(price, customer.signupDate(), today);
        return price.plus(tax(price, command.country()));
    }

    Money subscriptionPrice(Plan plan, int seats) {
        int extraSeats = Math.max(0, seats - plan.includedSeats());
        return plan.basePrice().plus(plan.extraSeatPrice().times(extraSeats));
    }

    Money applyCycle(Money monthly, BillingCycle cycle) {
        return cycle == BillingCycle.ANNUAL ? monthly.times(MONTHS_PER_YEAR).discount(ANNUAL_DISCOUNT) : monthly;
    }

    Money applyCoupon(Money price, String couponCode, LocalDate signupDate, LocalDate today) {
        return Coupon.fromCode(couponCode)
                .map(coupon -> coupon.apply(price, signupDate, today))
                .orElse(price);
    }

    Money applyLoyalty(Money price, LocalDate signupDate, LocalDate today) {
        boolean loyal = ChronoUnit.YEARS.between(signupDate, today) >= LOYALTY_MIN_YEARS;
        return loyal ? price.discount(LOYALTY_DISCOUNT) : price;
    }

    Money tax(Money price, String country) {
        return TAXED_COUNTRY.equals(country) ? price.times(BR_TAX_RATE) : Money.ZERO;
    }
}
