package br.ufg.inf.billing;

/** Falha de regra de negócio: a cobrança não pode ser feita. */
public class BillingException extends RuntimeException {

    public BillingException(String message) {
        super(message);
    }
}
