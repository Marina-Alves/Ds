package br.ufg.inf.billing;

import br.ufg.inf.billing.domain.Money;

public class PaymentDeclinedException extends BillingException {

    public PaymentDeclinedException(String customerId, Money amount) {
        super("Pagamento recusado para o cliente " + customerId + " no valor de R$ " + amount);
    }
}
