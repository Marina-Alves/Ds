package br.ufg.inf.billing.pricing;

import br.ufg.inf.billing.BillingCommand;
import br.ufg.inf.billing.domain.BillingCycle;
import br.ufg.inf.billing.domain.Customer;
import br.ufg.inf.billing.domain.Money;
import br.ufg.inf.billing.domain.Plan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Regras de preço testadas isoladamente: classe pura, sem dublês. */
class PricingCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 10);
    private static final Customer NEW_CUSTOMER = new Customer("c", "Ana", TODAY.minusDays(10), true);
    private static final Customer REGULAR_CUSTOMER = new Customer("c", "Ana", TODAY.minusDays(200), true);
    private static final Customer LOYAL_CUSTOMER = new Customer("c", "Ana", TODAY.minusYears(3), true);

    private final PricingCalculator calculator = new PricingCalculator();

    private Money total(Plan plan, int seats, BillingCycle cycle, String coupon, String country,
                        Customer customer, LocalDate today) {
        BillingCommand command = new BillingCommand("c", plan, seats, coupon, cycle, "tok", "a@b.c", country);
        return calculator.calculate(command, customer, today);
    }

    private Money total(Plan plan, int seats, String coupon, Customer customer) {
        return total(plan, seats, BillingCycle.MONTHLY, coupon, "US", customer, TODAY);
    }

    @ParameterizedTest(name = "{0} com {1} assento(s) = {2}")
    @CsvSource({
            "BASIC, 1, 29.90",
            "BASIC, 3, 49.70",
            "PRO, 5, 99.90",
            "PRO, 8, 144.90",
            "ENTERPRISE, 20, 499.90",
            "ENTERPRISE, 25, 549.90"
    })
    void precoDoPlanoComAssentosAdicionais(Plan plan, int seats, String expected) {
        assertEquals(Money.of(expected), total(plan, seats, null, REGULAR_CUSTOMER));
    }

    @Test
    void cicloAnualCobraDozeMesesCom15PorCentoDeDesconto() {
        // 99.90 * 12 * 0.85 = 1018.98
        assertEquals(Money.of("1018.98"),
                total(Plan.PRO, 5, BillingCycle.ANNUAL, null, "US", REGULAR_CUSTOMER, TODAY));
    }

    @Test
    void welcome10SoValeAte30DiasDeCadastro() {
        assertEquals(Money.of("89.91"), total(Plan.PRO, 5, "WELCOME10", NEW_CUSTOMER));
        assertEquals(Money.of("99.90"), total(Plan.PRO, 5, "WELCOME10", REGULAR_CUSTOMER));
    }

    @Test
    void blackFridaySoValeEmNovembro() {
        LocalDate november = LocalDate.of(2026, 11, 27);
        Customer customer = new Customer("c", "Ana", november.minusDays(200), true);
        assertEquals(Money.of("69.93"),
                total(Plan.PRO, 5, BillingCycle.MONTHLY, "BLACKFRIDAY", "US", customer, november));
        assertEquals(Money.of("99.90"), total(Plan.PRO, 5, "BLACKFRIDAY", REGULAR_CUSTOMER));
    }

    @Test
    void fixed20NuncaDeixaOValorNegativo() {
        assertEquals(Money.of("79.90"), total(Plan.PRO, 5, "FIXED20", REGULAR_CUSTOMER));
        assertEquals(Money.of("9.90"), total(Plan.BASIC, 1, "FIXED20", REGULAR_CUSTOMER));
    }

    @Test
    void cupomDesconhecidoOuVazioEIgnorado() {
        assertEquals(Money.of("99.90"), total(Plan.PRO, 5, "NAOEXISTE", REGULAR_CUSTOMER));
        assertEquals(Money.of("99.90"), total(Plan.PRO, 5, "", REGULAR_CUSTOMER));
    }

    @Test
    void clienteComDoisAnosOuMaisGanhaFidelidade() {
        // 499.90 * 0.95 = 474.905 -> 474.91
        assertEquals(Money.of("474.91"), total(Plan.ENTERPRISE, 20, null, LOYAL_CUSTOMER));
    }

    @Test
    void impostoDe5PorCentoSoNoBrasil() {
        // 99.90 * 1.05 = 104.895 -> 104.90
        assertEquals(Money.of("104.90"),
                total(Plan.PRO, 5, BillingCycle.MONTHLY, null, "BR", REGULAR_CUSTOMER, TODAY));
        assertEquals(Money.of("99.90"),
                total(Plan.PRO, 5, BillingCycle.MONTHLY, null, null, REGULAR_CUSTOMER, TODAY));
    }

    @Test
    void descontosSeAcumulamNaOrdemDoLegado() {
        // anual 1018.98 -> WELCOME10 917.082 -> imposto BR 962.9361 -> 962.94
        assertEquals(Money.of("962.94"),
                total(Plan.PRO, 5, BillingCycle.ANNUAL, "WELCOME10", "BR", NEW_CUSTOMER, TODAY));
    }

    @Test
    void comandoRejeitaQuantidadeDeAssentosInvalida() {
        assertThrows(IllegalArgumentException.class,
                () -> new BillingCommand("c", Plan.PRO, 0, null, BillingCycle.MONTHLY, "tok", "a@b.c", "BR"));
    }
}
