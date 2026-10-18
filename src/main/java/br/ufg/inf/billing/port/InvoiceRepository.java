package br.ufg.inf.billing.port;

import br.ufg.inf.billing.domain.Invoice;

public interface InvoiceRepository {

    /** @throws InfrastructureException se a fatura não puder ser gravada */
    void save(Invoice invoice);
}
