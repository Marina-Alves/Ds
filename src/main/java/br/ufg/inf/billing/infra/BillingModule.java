package br.ufg.inf.billing.infra;

import br.ufg.inf.billing.BillingService;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;

/** Composition root: único ponto que conhece as implementações concretas de infraestrutura. */
public final class BillingModule {

    private BillingModule() {
    }

    public static BillingService productionBillingService() {
        ConnectionFactory connections = ConnectionFactory.fromEnvironment();
        return new BillingService(
                new JdbcCustomerRepository(connections),
                new JdbcInvoiceRepository(connections),
                new HttpPaymentGateway(HttpClient.newHttpClient(),
                        URI.create("https://api.payments.billingco.com/v1/charges")),
                new SmtpNotificationService("smtp.billingco.internal", 25),
                Clock.systemDefaultZone());
    }
}
