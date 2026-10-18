# SaaS Billing Co. — Atividade prática de Refatoração

Implantação e Entrega de Software — 2026/2 (INF/UFG)

Núcleo financeiro da plataforma SaaS Billing Co.: cálculo mensal de assinaturas,
cupons promocionais, cobrança no cartão de crédito e envio de recibo por e-mail.

O relatório técnico está em [RELATORIO.md](RELATORIO.md).

## Como rodar

Requer Java 21 e Maven 3.9+.

```bash
mvn test   # 30 testes unitários, sem banco, gateway ou SMTP
```

## Estrutura

```
src/main/java/br/ufg/inf/billing/
├── BillingService.java      fachada que orquestra a cobrança
├── BillingCommand.java      objeto de parâmetros da cobrança
├── domain/                  Money, Plan, BillingCycle, Customer, Invoice
├── pricing/                 PricingCalculator e Coupon (regras puras)
├── port/                    interfaces dos serviços externos
└── infra/                   adaptadores JDBC, HTTP e SMTP + BillingModule
```

## Histórico

| Commit | Etapa |
|---|---|
| 1 | Código legado base |
| 2 | Value Objects (`Money`, `BillingCommand`, `Plan`, `BillingCycle`) |
| 3 | Portas de infraestrutura, adaptadores, `Clock` injetado, fim dos `catch (Exception)` |
| 4 | `PricingCalculator`, cupons polimórficos, remoção de código morto, suíte verde |
