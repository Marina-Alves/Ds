package br.ufg.inf.billing.port;

import br.ufg.inf.billing.domain.Money;

public interface PaymentGateway {

    /**
     * Cobra o valor no cartão representado pelo token.
     *
     * @return resultado aprovado ou recusado pela operadora
     * @throws InfrastructureException se o gateway não puder ser contatado
     */
    PaymentResult charge(String cardToken, Money amount);
}
