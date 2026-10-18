package br.ufg.inf.billing;

public class InactiveCustomerException extends BillingException {

    public InactiveCustomerException(String customerId) {
        super("Cliente inativo: " + customerId);
    }
}
