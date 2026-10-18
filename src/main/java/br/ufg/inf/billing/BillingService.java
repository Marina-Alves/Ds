package br.ufg.inf.billing;

import br.ufg.inf.billing.domain.BillingCycle;
import br.ufg.inf.billing.domain.Customer;
import br.ufg.inf.billing.domain.Invoice;
import br.ufg.inf.billing.domain.Money;
import br.ufg.inf.billing.domain.Plan;
import br.ufg.inf.billing.port.CustomerRepository;
import br.ufg.inf.billing.port.InvoiceRepository;
import br.ufg.inf.billing.port.NotificationException;
import br.ufg.inf.billing.port.NotificationService;
import br.ufg.inf.billing.port.PaymentGateway;
import br.ufg.inf.billing.port.PaymentResult;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Núcleo financeiro da SaaS Billing Co.
 * Calcula a mensalidade, aplica cupons, cobra no cartão, grava a fatura e envia o recibo.
 */
public class BillingService {

    private static final System.Logger LOG = System.getLogger(BillingService.class.getName());

    private static final boolean USE_OLD_TAX = false;

    private static final BigDecimal ANNUAL_MONTHS_DISCOUNT = new BigDecimal("0.15");
    private static final BigDecimal WELCOME_DISCOUNT = new BigDecimal("0.10");
    private static final BigDecimal BLACK_FRIDAY_DISCOUNT = new BigDecimal("0.30");
    private static final Money FIXED_COUPON_VALUE = Money.of("20");
    private static final BigDecimal LOYALTY_DISCOUNT = new BigDecimal("0.05");
    private static final BigDecimal BR_TAX_RATE = new BigDecimal("0.05");

    private final CustomerRepository customers;
    private final InvoiceRepository invoices;
    private final PaymentGateway paymentGateway;
    private final NotificationService notifications;
    private final Clock clock;

    public BillingService(CustomerRepository customers, InvoiceRepository invoices,
                          PaymentGateway paymentGateway, NotificationService notifications, Clock clock) {
        this.customers = Objects.requireNonNull(customers);
        this.invoices = Objects.requireNonNull(invoices);
        this.paymentGateway = Objects.requireNonNull(paymentGateway);
        this.notifications = Objects.requireNonNull(notifications);
        this.clock = Objects.requireNonNull(clock);
    }

    public BillingResult processBilling(BillingCommand command) {
        Customer customer = customers.findById(command.customerId())
                .orElseThrow(() -> new CustomerNotFoundException(command.customerId()));
        if (!customer.active()) {
            throw new InactiveCustomerException(customer.id());
        }

        LocalDate today = LocalDate.now(clock);
        Money total = calculateTotal(command, customer, today);

        PaymentResult payment = paymentGateway.charge(command.cardToken(), total);
        if (!payment.approved()) {
            throw new PaymentDeclinedException(customer.id(), total);
        }

        Invoice invoice = new Invoice(customer.id(), total, payment.transactionId(), today);
        invoices.save(invoice);

        boolean receiptSent = true;
        try {
            notifications.sendReceipt(customer, invoice, command.email());
        } catch (NotificationException e) {
            // o pagamento já foi feito: falha no recibo não desfaz a cobrança, mas fica registrada
            LOG.log(System.Logger.Level.WARNING, "Recibo não enviado para o cliente " + customer.id(), e);
            receiptSent = false;
        }
        return new BillingResult(invoice, receiptSent);
    }

    private Money calculateTotal(BillingCommand command, Customer customer, LocalDate today) {
        LocalDate signupDate = customer.signupDate();

        // calcula o preço do plano
        Plan plan = command.plan();
        Money base = plan.basePrice();
        if (command.seats() > plan.includedSeats()) {
            base = base.plus(plan.extraSeatPrice().times(command.seats() - plan.includedSeats()));
        }

        // plano anual: 12 meses com 15% de desconto
        if (command.cycle() == BillingCycle.ANNUAL) {
            base = base.times(12).discount(ANNUAL_MONTHS_DISCOUNT);
        }

        // cupons
        if (command.hasCoupon()) {
            String coupon = command.couponCode();
            if (coupon.equals("WELCOME10")) {
                if (ChronoUnit.DAYS.between(signupDate, today) <= 30) {
                    base = base.discount(WELCOME_DISCOUNT);
                }
            } else if (coupon.equals("BLACKFRIDAY")) {
                if (today.getMonth() == Month.NOVEMBER) {
                    base = base.discount(BLACK_FRIDAY_DISCOUNT);
                }
            } else if (coupon.equals("FIXED20")) {
                base = base.minus(FIXED_COUPON_VALUE).atLeastZero();
            }
        }

        // fidelidade: clientes com 2 anos ou mais ganham 5%
        if (ChronoUnit.YEARS.between(signupDate, today) >= 2) {
            base = base.discount(LOYALTY_DISCOUNT);
        }

        // imposto
        Money tax;
        if (USE_OLD_TAX) {
            tax = base.times(new BigDecimal("0.02"));
        } else {
            if ("BR".equals(command.country())) {
                tax = base.times(BR_TAX_RATE);
            } else {
                tax = Money.ZERO;
            }
        }
        return base.plus(tax).rounded();
    }

    // usado na versão 1 do sistema
    private double calculateLegacyDiscount(double value) {
        return value * 0.9;
    }

    // TODO remover depois da migração
    private double oldTaxRate(String country) {
        if (country.equals("BR")) {
            return 0.02;
        }
        return 0;
    }
}
