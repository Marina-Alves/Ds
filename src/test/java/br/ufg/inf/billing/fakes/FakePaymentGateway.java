package br.ufg.inf.billing.fakes;

import br.ufg.inf.billing.domain.Money;
import br.ufg.inf.billing.port.PaymentGateway;
import br.ufg.inf.billing.port.PaymentResult;

import java.util.ArrayList;
import java.util.List;

public class FakePaymentGateway implements PaymentGateway {

    private final List<Money> charges = new ArrayList<>();
    private boolean declineAll;

    public void declineAll() {
        declineAll = true;
    }

    @Override
    public PaymentResult charge(String cardToken, Money amount) {
        if (declineAll) {
            return PaymentResult.declined();
        }
        charges.add(amount);
        return PaymentResult.approved("tx-" + charges.size());
    }

    public List<Money> charges() {
        return charges;
    }
}
