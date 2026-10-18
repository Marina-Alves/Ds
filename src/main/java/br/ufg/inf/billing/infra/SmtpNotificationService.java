package br.ufg.inf.billing.infra;

import br.ufg.inf.billing.domain.Customer;
import br.ufg.inf.billing.domain.Invoice;
import br.ufg.inf.billing.port.NotificationException;
import br.ufg.inf.billing.port.NotificationService;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;

public class SmtpNotificationService implements NotificationService {

    private final String host;
    private final int port;

    public SmtpNotificationService(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public void sendReceipt(Customer customer, Invoice invoice, String email) {
        try (Socket socket = new Socket(host, port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            out.println("HELO billingco.com");
            out.println("MAIL FROM:<billing@billingco.com>");
            out.println("RCPT TO:<" + email + ">");
            out.println("DATA");
            out.println("Subject: Recibo SaaS Billing Co.");
            out.println();
            out.println("Ola " + customer.name() + ", recebemos o pagamento de R$ " + invoice.amount()
                    + ". Transacao: " + invoice.transactionId());
            out.println(".");
            out.println("QUIT");
        } catch (IOException e) {
            throw new NotificationException("Falha ao enviar recibo para " + email, e);
        }
    }
}
