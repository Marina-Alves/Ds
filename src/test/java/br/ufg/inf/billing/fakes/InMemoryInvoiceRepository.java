package br.ufg.inf.billing.fakes;

import br.ufg.inf.billing.domain.Invoice;
import br.ufg.inf.billing.port.InvoiceRepository;

import java.util.ArrayList;
import java.util.List;

public class InMemoryInvoiceRepository implements InvoiceRepository {

    private final List<Invoice> saved = new ArrayList<>();

    @Override
    public void save(Invoice invoice) {
        saved.add(invoice);
    }

    public List<Invoice> saved() {
        return saved;
    }
}
