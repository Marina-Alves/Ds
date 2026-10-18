package br.ufg.inf.billing;

public class CustomerNotFoundException extends BillingException {

    public CustomerNotFoundException(String customerId) {
        super("Cliente não encontrado: " + customerId);
    }
}
