package br.ufg.inf.billing.infra;

import br.ufg.inf.billing.domain.Customer;
import br.ufg.inf.billing.port.CustomerRepository;
import br.ufg.inf.billing.port.InfrastructureException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class JdbcCustomerRepository implements CustomerRepository {

    private static final String FIND_BY_ID = "SELECT name, signup_date, active FROM customers WHERE id = ?";

    private final ConnectionFactory connections;

    public JdbcCustomerRepository(ConnectionFactory connections) {
        this.connections = connections;
    }

    @Override
    public Optional<Customer> findById(String customerId) {
        try (Connection conn = connections.open();
             PreparedStatement ps = conn.prepareStatement(FIND_BY_ID)) {
            ps.setString(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Customer(customerId, rs.getString("name"),
                        rs.getDate("signup_date").toLocalDate(), rs.getBoolean("active")));
            }
        } catch (SQLException e) {
            throw new InfrastructureException("Falha ao consultar o cliente " + customerId, e);
        }
    }
}
