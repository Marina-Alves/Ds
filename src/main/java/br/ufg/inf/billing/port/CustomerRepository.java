package br.ufg.inf.billing.port;

import br.ufg.inf.billing.domain.Customer;

import java.util.Optional;

public interface CustomerRepository {

    /** @throws InfrastructureException se o armazenamento estiver indisponível */
    Optional<Customer> findById(String customerId);
}
