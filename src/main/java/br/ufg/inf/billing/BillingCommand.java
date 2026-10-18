package br.ufg.inf.billing;

import br.ufg.inf.billing.domain.BillingCycle;
import br.ufg.inf.billing.domain.Plan;

import java.util.Objects;

/**
 * Objeto de parâmetros de uma cobrança. Substitui os oito argumentos soltos
 * de {@code processBilling(String, String, int, String, boolean, String, String, String)}.
 *
 * @param couponCode opcional; {@code null} ou vazio significa sem cupom
 * @param country    código ISO do país de faturamento (ex.: "BR")
 */
public record BillingCommand(String customerId, Plan plan, int seats, String couponCode,
                             BillingCycle cycle, String cardToken, String email, String country) {

    public BillingCommand {
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(cycle, "cycle");
        Objects.requireNonNull(cardToken, "cardToken");
        Objects.requireNonNull(email, "email");
        if (seats < 1) {
            throw new IllegalArgumentException("seats deve ser >= 1, recebido: " + seats);
        }
    }

    public boolean hasCoupon() {
        return couponCode != null && !couponCode.isBlank();
    }
}
