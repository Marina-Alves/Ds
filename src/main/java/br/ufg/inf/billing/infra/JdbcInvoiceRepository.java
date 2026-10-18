package br.ufg.inf.billing.infra;

import br.ufg.inf.billing.domain.Invoice;
import br.ufg.inf.billing.port.InfrastructureException;
import br.ufg.inf.billing.port.InvoiceRepository;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class JdbcInvoiceRepository implements InvoiceRepository {

    private static final String INSERT =
            "INSERT INTO invoices (customer_id, amount, transaction_id, issued_at) VALUES (?, ?, ?, ?)";

    private final ConnectionFactory connections;

    public JdbcInvoiceRepository(ConnectionFactory connections) {
        this.connections = connections;
    }

    @Override
    public void save(Invoice invoice) {
        try (Connection conn = connections.open();
             PreparedStatement ps = conn.prepareStatement(INSERT)) {
            ps.setString(1, invoice.customerId());
            ps.setBigDecimal(2, invoice.amount().amount());
            ps.setString(3, invoice.transactionId());
            ps.setDate(4, Date.valueOf(invoice.issuedAt()));
            ps.executeUpdate();
        } catch (SQLException e) {
            // o cartão já foi cobrado: a transação vai na mensagem para permitir a conciliação
            throw new InfrastructureException("Falha ao gravar a fatura do cliente " + invoice.customerId()
                    + " (transação " + invoice.transactionId() + " já cobrada)", e);
        }
    }
}
