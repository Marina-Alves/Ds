package br.ufg.inf.billing.pricing;

import br.ufg.inf.billing.BillingCommand;
import br.ufg.inf.billing.domain.BillingCycle;
import br.ufg.inf.billing.domain.Customer;
import br.ufg.inf.billing.domain.Plan;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Golden master: compara o PricingCalculator com a fórmula legada em todas as
 * combinações relevantes de plano, assentos, ciclo, cupom, país e datas.
 *
 * <p>Única diferença aceita: o legado calculava em {@code double} e, quando o valor exato
 * terminava em meio centavo (ex.: 29,90 × 0,95 = 28,405), o erro binário (28,40499…)
 * fazia o {@code Math.round} arredondar para baixo. O Money arredonda o valor exato com
 * HALF_UP, como a regra pretendia. O teste garante que toda divergência é exatamente esse
 * empate, com 1 centavo a mais, e nenhuma outra.
 */
class LegacyPricingCharacterizationTest {

    private static final int[] SEATS = {1, 2, 4, 5, 6, 7, 19, 20, 21, 37, 100};
    private static final String[] COUPONS = {null, "", "WELCOME10", "BLACKFRIDAY", "FIXED20", "INVALIDO"};
    private static final String[] COUNTRIES = {"BR", "US", null};
    private static final LocalDate[] TODAYS = {
            LocalDate.of(2026, 3, 10), LocalDate.of(2026, 11, 27), LocalDate.of(2028, 2, 29)};
    private static final int[] SIGNUP_DAYS_AGO = {0, 10, 30, 31, 365, 729, 730, 731, 1500};

    private final PricingCalculator calculator = new PricingCalculator();

    @Test
    void novoMotorReproduzOValorCobradoPeloLegado() {
        List<String> divergences = new ArrayList<>();
        int cases = 0;
        int halfCentTies = 0;
        for (Plan plan : Plan.values())
            for (int seats : SEATS)
                for (BillingCycle cycle : BillingCycle.values())
                    for (String coupon : COUPONS)
                        for (String country : COUNTRIES)
                            for (LocalDate today : TODAYS)
                                for (int daysAgo : SIGNUP_DAYS_AGO) {
                                    cases++;
                                    LocalDate signup = today.minusDays(daysAgo);
                                    BigDecimal legacy = BigDecimal.valueOf(LegacyPricingReference.total(plan.name(), seats,
                                            coupon, cycle == BillingCycle.ANNUAL, country, signup, today));
                                    BillingCommand command = new BillingCommand("c", plan, seats, coupon, cycle,
                                            "tok", "a@b.c", country);
                                    Customer customer = new Customer("c", "n", signup, true);
                                    BigDecimal refactored = calculator.calculate(command, customer, today).amount();
                                    BigDecimal exact = calculator.unroundedTotal(command, customer, today).amount();
                                    if (legacy.compareTo(refactored) == 0) {
                                        continue;
                                    }
                                    if (isHalfCentTie(exact) && refactored.subtract(legacy).compareTo(ONE_CENT) == 0) {
                                        halfCentTies++;
                                    } else {
                                        divergences.add(String.join(" ", Arrays.asList(plan.name(), "seats=" + seats,
                                                cycle.name(), "coupon=" + coupon, "country=" + country,
                                                "today=" + today, "signup=" + signup,
                                                "legado=" + legacy, "novo=" + refactored)));
                                    }
                                }
        assertTrue(divergences.isEmpty(), divergences.size() + " de " + cases + " casos divergem:\n"
                + String.join("\n", divergences.subList(0, Math.min(20, divergences.size()))));
        System.out.printf("Golden master: %d casos, %d idênticos, %d empates de meio centavo corrigidos%n",
                cases, cases - halfCentTies, halfCentTies);
    }

    private static final BigDecimal ONE_CENT = new BigDecimal("0.01");

    /** Valor exato cuja terceira casa decimal é 5 e não tem mais nada depois (ex.: 28.405). */
    private static boolean isHalfCentTie(BigDecimal exact) {
        BigDecimal thousandths = exact.movePointRight(3);
        return thousandths.stripTrailingZeros().scale() <= 0
                && thousandths.remainder(BigDecimal.TEN).abs().compareTo(new BigDecimal("5")) == 0;
    }
}
