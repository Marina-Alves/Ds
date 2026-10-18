package br.ufg.inf.billing.port;

/** Falha técnica de um serviço externo (banco, gateway, SMTP), com a causa original preservada. */
public class InfrastructureException extends RuntimeException {

    public InfrastructureException(String message, Throwable cause) {
        super(message, cause);
    }
}
