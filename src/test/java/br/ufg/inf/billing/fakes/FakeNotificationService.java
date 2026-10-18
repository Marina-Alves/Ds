package br.ufg.inf.billing.fakes;

import br.ufg.inf.billing.domain.Customer;
import br.ufg.inf.billing.domain.Invoice;
import br.ufg.inf.billing.port.NotificationException;
import br.ufg.inf.billing.port.NotificationService;

import java.util.ArrayList;
import java.util.List;

public class FakeNotificationService implements NotificationService {

    private final List<String> recipients = new ArrayList<>();
    private boolean failing;

    public void simulateSmtpOutage() {
        failing = true;
    }

    @Override
    public void sendReceipt(Customer customer, Invoice invoice, String email) {
        if (failing) {
            throw new NotificationException("SMTP fora do ar", new RuntimeException());
        }
        recipients.add(email);
    }

    public List<String> recipients() {
        return recipients;
    }
}
