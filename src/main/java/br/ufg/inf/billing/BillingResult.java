package br.ufg.inf.billing;

import br.ufg.inf.billing.domain.Invoice;

/**
 * Resultado de uma cobrança concluída.
 *
 * @param receiptSent {@code false} quando o pagamento foi feito mas o recibo não pôde ser enviado
 */
public record BillingResult(Invoice invoice, boolean receiptSent) {
}
