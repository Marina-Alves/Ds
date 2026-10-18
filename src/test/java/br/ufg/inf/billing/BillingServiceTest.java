package br.ufg.inf.billing;

import br.ufg.inf.billing.domain.BillingCycle;
import br.ufg.inf.billing.domain.Customer;
import br.ufg.inf.billing.domain.Money;
import br.ufg.inf.billing.domain.Plan;
import br.ufg.inf.billing.fakes.FakeNotificationService;
import br.ufg.inf.billing.fakes.FakePaymentGateway;
import br.ufg.inf.billing.fakes.InMemoryCustomerRepository;
import br.ufg.inf.billing.fakes.InMemoryInvoiceRepository;
import br.ufg.inf.billing.port.InfrastructureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testa a orquestração com dublês em memória: sem banco, sem gateway, sem SMTP. */
class BillingServiceTest {

    private static final Clock MARCH_10_2026 = Clock.fixed(Instant.parse("2026-03-10T12:00:00Z"), ZoneOffset.UTC);

    private InMemoryCustomerRepository customers;
    private InMemoryInvoiceRepository invoices;
    private FakePaymentGateway payments;
    private FakeNotificationService notifications;
    private BillingService service;

    @BeforeEach
    void setUp() {
        customers = new InMemoryCustomerRepository()
                .add(new Customer("c1", "Ana", LocalDate.of(2025, 6, 1), true))
                .add(new Customer("inactive", "Bia", LocalDate.of(2025, 6, 1), false));
        invoices = new InMemoryInvoiceRepository();
        payments = new FakePaymentGateway();
        notifications = new FakeNotificationService();
        service = new BillingService(customers, invoices, payments, notifications, MARCH_10_2026);
    }

    private static BillingCommand proMonthly(String customerId) {
        return new BillingCommand(customerId, Plan.PRO, 5, null, BillingCycle.MONTHLY, "tok", "ana@ex.com", "BR");
    }

    @Test
    void cobraGravaFaturaEEnviaRecibo() {
        BillingResult result = service.processBilling(proMonthly("c1"));

        // 99.90 + 5% de imposto = 104.895 -> 104.90
        assertEquals(Money.of("104.90"), result.invoice().amount());
        assertEquals(LocalDate.of(2026, 3, 10), result.invoice().issuedAt());
        assertEquals("tx-1", result.invoice().transactionId());
        assertTrue(result.receiptSent());
        assertEquals(List.of(Money.of("104.90")), payments.charges());
        assertEquals(List.of(result.invoice()), invoices.saved());
        assertEquals(List.of("ana@ex.com"), notifications.recipients());
    }

    @Test
    void clienteInexistenteNaoECobrado() {
        assertThrows(CustomerNotFoundException.class, () -> service.processBilling(proMonthly("nope")));
        assertTrue(payments.charges().isEmpty());
    }

    @Test
    void clienteInativoNaoECobrado() {
        assertThrows(InactiveCustomerException.class, () -> service.processBilling(proMonthly("inactive")));
        assertTrue(payments.charges().isEmpty());
    }

    @Test
    void pagamentoRecusadoNaoGeraFatura() {
        payments.declineAll();

        assertThrows(PaymentDeclinedException.class, () -> service.processBilling(proMonthly("c1")));
        assertTrue(invoices.saved().isEmpty());
        assertTrue(notifications.recipients().isEmpty());
    }

    @Test
    void falhaNoSmtpNaoDesfazACobrancaMasFicaVisivelNoResultado() {
        notifications.simulateSmtpOutage();

        BillingResult result = service.processBilling(proMonthly("c1"));

        assertFalse(result.receiptSent());
        assertEquals(1, invoices.saved().size());
    }

    @Test
    void falhaDeInfraestruturaNaoESilenciada() {
        customers.simulateOutage();

        assertThrows(InfrastructureException.class, () -> service.processBilling(proMonthly("c1")));
    }

    @Test
    void cupomBlackFridaySoValeEmNovembroComRelogioInjetado() {
        Clock november = Clock.fixed(Instant.parse("2026-11-27T12:00:00Z"), ZoneOffset.UTC);
        BillingService blackFriday = new BillingService(customers, invoices, payments, notifications, november);
        BillingCommand command = new BillingCommand("c1", Plan.PRO, 5, "BLACKFRIDAY",
                BillingCycle.MONTHLY, "tok", "ana@ex.com", "BR");

        // 99.90 * 0.70 = 69.93; * 1.05 = 73.4265 -> 73.43
        assertEquals(Money.of("73.43"), blackFriday.processBilling(command).invoice().amount());
        // março: cupom ignorado
        assertEquals(Money.of("104.90"), service.processBilling(command).invoice().amount());
    }
}
