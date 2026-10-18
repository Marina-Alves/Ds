package br.ufg.inf.billing.fakes;

import br.ufg.inf.billing.domain.Customer;
import br.ufg.inf.billing.port.CustomerRepository;
import br.ufg.inf.billing.port.InfrastructureException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryCustomerRepository implements CustomerRepository {

    private final Map<String, Customer> customers = new HashMap<>();
    private boolean unavailable;

    public InMemoryCustomerRepository add(Customer customer) {
        customers.put(customer.id(), customer);
        return this;
    }

    public void simulateOutage() {
        unavailable = true;
    }

    @Override
    public Optional<Customer> findById(String customerId) {
        if (unavailable) {
            throw new InfrastructureException("banco fora do ar", new RuntimeException());
        }
        return Optional.ofNullable(customers.get(customerId));
    }
}
