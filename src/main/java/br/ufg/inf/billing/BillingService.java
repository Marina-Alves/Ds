package br.ufg.inf.billing;

import br.ufg.inf.billing.domain.Customer;
import br.ufg.inf.billing.domain.Invoice;
import br.ufg.inf.billing.domain.Money;
import br.ufg.inf.billing.port.CustomerRepository;
import br.ufg.inf.billing.port.InvoiceRepository;
import br.ufg.inf.billing.port.NotificationException;
import br.ufg.inf.billing.port.NotificationService;
import br.ufg.inf.billing.port.PaymentGateway;
import br.ufg.inf.billing.port.PaymentResult;
import br.ufg.inf.billing.pricing.PricingCalculator;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Fachada do faturamento: orquestra cliente, precificação, cobrança, fatura e recibo.
 * Não contém regra de preço ({@link PricingCalculator}) nem detalhe de I/O (pacote {@code infra}).
 */
public class BillingService {

    private static final System.Logger LOG = System.getLogger(BillingService.class.getName());

    private final CustomerRepository customers;
    private final InvoiceRepository invoices;
    private final PaymentGateway paymentGateway;
    private final NotificationService notifications;
    private final Clock clock;
    private final PricingCalculator pricing = new PricingCalculator();

    public BillingService(CustomerRepository customers, InvoiceRepository invoices,
                          PaymentGateway paymentGateway, NotificationService notifications, Clock clock) {
        this.customers = Objects.requireNonNull(customers);
        this.invoices = Objects.requireNonNull(invoices);
        this.paymentGateway = Objects.requireNonNull(paymentGateway);
        this.notifications = Objects.requireNonNull(notifications);
        this.clock = Objects.requireNonNull(clock);
    }

    public BillingResult processBilling(BillingCommand command) {
        Customer customer = findActiveCustomer(command.customerId());
        LocalDate today = LocalDate.now(clock);
        Money total = pricing.calculate(command, customer, today);
        String transactionId = charge(command.cardToken(), customer, total);
        Invoice invoice = new Invoice(customer.id(), total, transactionId, today);
        invoices.save(invoice);
        return new BillingResult(invoice, sendReceipt(customer, invoice, command.email()));
    }

    private Customer findActiveCustomer(String customerId) {
        Customer customer = customers.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
        if (!customer.active()) {
            throw new InactiveCustomerException(customerId);
        }
        return customer;
    }

    private String charge(String cardToken, Customer customer, Money total) {
        PaymentResult payment = paymentGateway.charge(cardToken, total);
        if (!payment.approved()) {
            throw new PaymentDeclinedException(customer.id(), total);
        }
        return payment.transactionId();
    }

    /** O pagamento já foi feito: falha no recibo não desfaz a cobrança, mas fica registrada. */
    private boolean sendReceipt(Customer customer, Invoice invoice, String email) {
        try {
            notifications.sendReceipt(customer, invoice, email);
            return true;
        } catch (NotificationException e) {
            LOG.log(System.Logger.Level.WARNING, "Recibo não enviado para o cliente " + customer.id(), e);
            return false;
        }
    }
}
