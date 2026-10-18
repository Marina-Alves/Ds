package br.ufg.inf.billing.infra;

import br.ufg.inf.billing.domain.Money;
import br.ufg.inf.billing.port.InfrastructureException;
import br.ufg.inf.billing.port.PaymentGateway;
import br.ufg.inf.billing.port.PaymentResult;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class HttpPaymentGateway implements PaymentGateway {

    private final HttpClient http;
    private final URI chargesEndpoint;

    public HttpPaymentGateway(HttpClient http, URI chargesEndpoint) {
        this.http = http;
        this.chargesEndpoint = chargesEndpoint;
    }

    @Override
    public PaymentResult charge(String cardToken, Money amount) {
        String json = "{\"token\":\"" + cardToken + "\",\"amount\":" + amount + ",\"currency\":\"BRL\"}";
        HttpRequest request = HttpRequest.newBuilder(chargesEndpoint)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return PaymentResult.declined();
            }
            return PaymentResult.approved(response.body().lines().findFirst().orElse(""));
        } catch (IOException e) {
            throw new InfrastructureException("Gateway de pagamento indisponível", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InfrastructureException("Cobrança interrompida", e);
        }
    }
}
