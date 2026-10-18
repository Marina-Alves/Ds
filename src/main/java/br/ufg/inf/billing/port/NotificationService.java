package br.ufg.inf.billing.port;

import br.ufg.inf.billing.domain.Customer;
import br.ufg.inf.billing.domain.Invoice;

public interface NotificationService {

    /** @throws NotificationException se o recibo não puder ser enviado */
    void sendReceipt(Customer customer, Invoice invoice, String email);
}
