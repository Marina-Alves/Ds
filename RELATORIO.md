# Relatório Técnico — Refatoração do núcleo financeiro da SaaS Billing Co.

Implantação e Entrega de Software — 2026/2 · Engenharia de Software · INF/UFG
Professora: Sofia Costa Paiva

Dupla: _[nome 1]_ · _[nome 2]_

---

## 1. Contexto

O núcleo financeiro concentrava, em um único método `BillingService.processBilling` (commit 1),
o cálculo da mensalidade, os cupons, a cobrança no cartão, a gravação da fatura e o envio do recibo.
A janela de manutenção tinha duas restrições:

1. nenhum comportamento de negócio existente pode ser quebrado;
2. a CI precisa rodar os testes unitários sem banco de dados nem servidor SMTP.

O código legado não tinha nenhum teste, e não dava para escrever um: o método abria conexão JDBC,
chamava o gateway por HTTP, abria socket SMTP e lia `LocalDate.now()` diretamente.

## 2. Matriz de Code Smells e Violações Arquiteturais

Linhas referentes a `src/main/java/br/ufg/inf/billing/BillingService.java` no **commit 1**.

| # | Linha / trecho | Code Smell ou princípio violado | "Juros" pagos (manutenção e entrega contínua) |
|---|---|---|---|
| 1 | 29–167, `processBilling` inteiro (117 linhas, complexidade ciclomática 25) | **Long Method** e violação do **Single Responsibility Principle**: preço, cupons, imposto, cobrança, persistência e e-mail no mesmo método (**God Method**) | Qualquer mudança, até em um cupom, mexe no método que cobra o cartão. Todo PR tem revisão cara e alto risco de regressão. CC 25 exige pelo menos 25 casos de teste só para cobrir os caminhos, e nenhum é viável. |
| 2 | 29–30, `processBilling(String customerId, String plan, int seats, String coupon, boolean annual, String cardToken, String email, String country)` | **Long Parameter List**, **Primitive Obsession** e **Flag Argument** (`boolean annual`) | Oito parâmetros, cinco deles `String`: trocar `email` por `cardToken` compila sem erro. O plano é validado só em tempo de execução (`"ERROR: invalid plan"`). Cada campo novo quebra a assinatura de todos os chamadores. |
| 3 | 51–114: `double base`, `29.90`, `base * 0.15`, `Math.round(total * 100.0) / 100.0`; 138: `setDouble` | **Primitive Obsession** para dinheiro (`double`) e **Magic Numbers** | Erro de ponto flutuante binário em valor financeiro. O teste de caracterização achou **512 de 32.076 combinações** em que o legado cobra 1 centavo a menos (ex.: 29,90 × 0,95 = 28,405 vira 28,40499… e arredonda para 28,40). Os percentuais aparecem soltos no código, sem nome que diga a regra. |
| 4 | 33–35 (`DriverManager.getConnection`), 117–128 (`HttpURLConnection`), 146–157 (`new Socket(... 25)`) | Violação do **Dependency Inversion Principle**: **dependência direta de I/O** e infraestrutura instanciada dentro da regra de negócio | Impossível testar sem PostgreSQL, gateway e SMTP de verdade. A CI não consegue rodar teste unitário, e um defeito de cálculo só aparece em produção. Trocar o provedor de pagamento obriga a editar a regra de preço. |
| 5 | 77 e 140, `LocalDate.now()` | **Dependência oculta do relógio do sistema** (teste não determinístico) | Os cupons `WELCOME10` (30 dias), `BLACKFRIDAY` (só em novembro) e a fidelidade (2 anos) dependem do dia em que o teste roda. Um teste que passa em outubro falha em novembro: build "flaky" e regra de Black Friday sem verificação antes da data. |
| 6 | 158–160 `catch (Exception e) { // ignora }`; 163–165 `catch (Exception e) { e.printStackTrace(); return "ERROR"; }` | **Erros mascarados** (*swallowed exceptions*) e **Pokémon Exception Handling** | Banco fora do ar, cartão recusado e `NullPointerException` viram o mesmo `"ERROR"`. Se a fatura falhar depois da cobrança, o cliente é cobrado sem fatura e ninguém fica sabendo (a stack trace vai para o stdout). Falha de SMTP some sem rastro. O MTTR sobe porque o diagnóstico depende de reproduzir em produção. |
| 7 | 33 e 142, conexão aberta sem `try-with-resources`; `conn.close()` só no caminho feliz | **Vazamento de recurso** | Todo retorno antecipado (`customer not found`, `inactive`, `invalid plan`, `payment declined`) e toda exceção deixam a conexão aberta, até esgotar o pool sob carga. |
| 8 | 27 `USE_OLD_TAX = false`, 104–106; 170–172 `calculateLegacyDiscount`; 175–180 `oldTaxRate` | **Dead Code** e **Speculative Generality** | Ramo e métodos que nunca executam, mas que o leitor precisa entender, e que confundem quem procura a regra de imposto vigente. Aumentam a superfície de manutenção e a cobertura aparente sem valor. |
| 9 | 29 (retorno `String`), 41, 47, 68, 128, 162, 165 | **Stringly-typed API** (resultado codificado em texto: `"SUCCESS: 104.9"`, `"ERROR: ..."`) | O chamador precisa fazer *parsing* de texto para saber se cobrou e quanto. Mudar uma mensagem quebra integrações silenciosamente. |

## 3. Refatorações aplicadas

Operações do catálogo de refatoração (Fowler) usadas em cada etapa, com o commit correspondente.

### 3.1 Primitive Obsession e imutabilidade (commit 2)

| Refatoração | Resultado |
|---|---|
| *Replace Primitive with Object* | `domain/Money`: `record` imutável sobre `BigDecimal`, com `plus`, `minus`, `times`, `discount`, `atLeastZero` e `rounded()` (HALF_UP para centavos). A igualdade compara o valor numérico (10.0 == 10.00). |
| *Introduce Parameter Object* | `BillingCommand` (`record`) substitui os oito parâmetros e valida a entrada no construtor compacto. |
| *Replace Type Code with Class* | `Plan` (enum com preço base, assentos incluídos e preço do assento extra) no lugar de `"BASIC"`/`"PRO"`/`"ENTERPRISE"`. |
| *Remove Flag Argument* | `BillingCycle.MONTHLY`/`ANNUAL` substitui `boolean annual`. |
| *Replace Magic Literal* | Percentuais viram constantes nomeadas. |

### 3.2 Inversão de dependência e desacoplamento de I/O (commit 3)

| Refatoração | Resultado |
|---|---|
| *Extract Interface* (portas) | `port/CustomerRepository`, `port/PaymentGateway`, `port/NotificationService` e também `port/InvoiceRepository`, porque a gravação da fatura era o quarto ponto de I/O direto. |
| *Move Function* (adaptadores) | `infra/JdbcCustomerRepository`, `infra/JdbcInvoiceRepository` (com `try-with-resources`), `infra/HttpPaymentGateway`, `infra/SmtpNotificationService`. `infra/BillingModule` é o *composition root*, o único lugar que conhece as classes concretas. |
| *Parameterize Function* / injeção de `Clock` | `BillingService` recebe `java.time.Clock` no construtor; `LocalDate.now(clock)` torna as datas determinísticas (`Clock.fixed` nos testes). |
| *Replace Error Code with Exception* | `CustomerNotFoundException`, `InactiveCustomerException` e `PaymentDeclinedException` (negócio); `InfrastructureException` e `NotificationException` (técnicas, com a causa original). `BillingResult` substitui a `String` de retorno. |

### 3.3 Extração de classes e métodos (commit 4)

| Refatoração | Resultado |
|---|---|
| *Extract Class* | `pricing/PricingCalculator`: classe de domínio pura, sem I/O e sem relógio (a data chega como argumento). |
| *Decompose Conditional* / *Extract Function* | `subscriptionPrice`, `applyCycle`, `applyCoupon`, `applyLoyalty`, `tax`. |
| *Replace Conditional with Polymorphism* | `pricing/Coupon`: cada cupom implementa a própria regra; um cupom novo não toca no calculador. |
| *Extract Function* na fachada | `processBilling` ficou com **7 linhas** e CC 1 (limite pedido: 25 linhas) e delega para `findActiveCustomer`, `charge` e `sendReceipt`. |

### 3.4 Código morto e erros mascarados (commits 3 e 4)

| Refatoração | Resultado |
|---|---|
| *Remove Dead Code* | Saíram `USE_OLD_TAX` e o ramo de imposto antigo, `calculateLegacyDiscount` e `oldTaxRate`. |
| Fim de `catch (Exception e)` | Nenhum `catch (Exception)` sobrou no código. Os adaptadores capturam só `SQLException`/`IOException` e relançam com a causa. A falha de recibo é a única tratada no serviço, e de forma explícita: o pagamento já foi feito, então ela não desfaz a cobrança, mas vai para o log (`WARNING`) e para `BillingResult.receiptSent = false`. A falha ao gravar a fatura depois da cobrança propaga com o ID da transação na mensagem, para permitir a conciliação. |

## 4. Métricas antes vs. depois

Medido no commit 1 (antes) e no commit 4 (depois). Linhas = linhas não vazias e sem comentário
no corpo do método; complexidade ciclomática (CC) = 1 + `if`/`for`/`while`/`catch`/`case`/`&&`/`||`/`?`.

| Métrica | Antes | Depois |
|---|---|---|
| Linhas do método principal (`processBilling`) | 117 | **7** |
| Complexidade ciclomática de `processBilling` | 25 | **1** |
| Maior CC de qualquer método do projeto | 25 | 4 (`HttpPaymentGateway.charge`) |
| Parâmetros de `processBilling` | 8 primitivos | 1 (`BillingCommand`) |
| Responsabilidades em `BillingService` | 6 (preço, cupom, imposto, cobrança, persistência, e-mail) | 1 (orquestrar) |
| Dependências diretas de I/O em `BillingService` (JDBC, HTTP, Socket) | 3 | **0** |
| Chamadas a `LocalDate.now()` sem `Clock` | 2 | **0** |
| Uso de `double` para dinheiro | 5 linhas | **0** |
| `catch (Exception e)` | 2 | **0** |
| Métodos / flags mortos | 3 | **0** |
| Conexões JDBC sem `try-with-resources` | 1 (vaza em 5 caminhos) | 0 |
| Classes de produção | 1 | 27 (média de ~20 linhas cada) |
| Testes unitários | 0 (inviável) | **30**, todos verdes |
| Infraestrutura para rodar os testes | banco + gateway + SMTP | **nenhuma** |
| Tempo da suíte | — | < 1 s |
| Combinações verificadas contra o legado (golden master) | — | 32.076 |

## 5. Garantia de comportamento

`pricing/LegacyPricingCharacterizationTest` é um teste *golden master*: copia a aritmética de preço
do commit 1 (`LegacyPricingReference`, só em `src/test`) e compara com o `PricingCalculator` em
**32.076 combinações** (3 planos × 11 quantidades de assentos × 2 ciclos × 6 cupons × 3 países ×
3 datas de referência × 9 datas de cadastro, incluindo os limites de 30 dias, novembro,
2 anos e 29 de fevereiro).

Resultado: **31.564 casos idênticos** e **512 com 1 centavo a mais**. O teste confirma que cada
uma dessas 512 diferenças é um empate exato de meio centavo (valor exato termina em 5 na terceira
casa) que o `double` do legado arredondava para baixo por erro de representação binária. Nenhuma
outra divergência é aceita.

### Mudanças de comportamento intencionais

| Mudança | Antes | Depois | Justificativa |
|---|---|---|---|
| Arredondamento em empate de meio centavo | 28,40 (erro de `double`) | 28,41 (HALF_UP) | A regra pretendida é o arredondamento comercial; o legado errava em 1,6% das combinações. **Decisão a validar com o negócio/financeiro.** |
| Sinalização de erro | `String` (`"ERROR: ..."`) | exceções tipadas e `BillingResult` | Erros deixam de ser mascarados. |
| Falha de infraestrutura | `"ERROR"` + stack trace no stdout | `InfrastructureException` com a causa | Requisito da atividade (item 2.4). |
| Falha ao enviar recibo | ignorada sem registro | log `WARNING` + `receiptSent = false` | A cobrança continua válida, como antes, mas a falha fica visível. |
| `seats < 1` | aceito (cobrava o plano base) | `IllegalArgumentException` | Entrada inválida passa a ser rejeitada na borda. |
| Valor enviado ao gateway | `104.9` (`double.toString`) | `104.90` | Formato monetário estável com 2 casas. |

Regras preservadas e cobertas por teste: preço base e assentos extras por plano, desconto anual de
15%, `WELCOME10` (10% até 30 dias de cadastro), `BLACKFRIDAY` (30% em novembro), `FIXED20`
(R$ 20 sem ficar negativo), cupom vazio ou desconhecido ignorado, fidelidade de 5% a partir de
2 anos, imposto de 5% só para `BR`, ordem de aplicação dos descontos e o fluxo
cliente → cobrança → fatura → recibo.

## 6. Arquitetura resultante

```
              ┌──────────────────────── BillingService (fachada) ────────────────────────┐
BillingCommand│ findActiveCustomer → PricingCalculator.calculate → charge → save → recibo │→ BillingResult
              └───────┬───────────────────────┬──────────────────┬───────────────┬────────┘
                      │ port                  │ port             │ port          │ port
             CustomerRepository        PaymentGateway     InvoiceRepository  NotificationService
                      │ infra                 │ infra            │ infra         │ infra
          JdbcCustomerRepository   HttpPaymentGateway  JdbcInvoiceRepository  SmtpNotificationService
```

`domain` e `pricing` não importam nada de `infra`; só `BillingModule` conhece os adaptadores.
Nos testes, os adaptadores são trocados por dublês em memória (`src/test/.../fakes`) e por
`Clock.fixed`.

## 7. Histórico no Git

| Commit | Mensagem |
|---|---|
| 1 | `chore: adiciona código legado base do núcleo de faturamento` |
| 2 | `refactor: introduz Value Objects e remove Primitive Obsession` |
| 3 | `refactor: extrai interfaces de infraestrutura e injeta dependências` |
| 4 | `refactor: extrai motor de precificação, remove código morto e valida suíte verde` |

O workflow `.github/workflows/ci.yml` roda `mvn -B test` a cada push, sem serviços externos.
