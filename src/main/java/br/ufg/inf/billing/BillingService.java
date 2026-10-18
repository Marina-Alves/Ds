package br.ufg.inf.billing;

import br.ufg.inf.billing.domain.BillingCycle;
import br.ufg.inf.billing.domain.Money;
import br.ufg.inf.billing.domain.Plan;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.ChronoUnit;

/**
 * Núcleo financeiro da SaaS Billing Co.
 * Calcula a mensalidade, aplica cupons, cobra no cartão, grava a fatura e envia o recibo.
 *
 * CÓDIGO LEGADO: mantido como foi recebido das equipes anteriores.
 */
public class BillingService {

    private static final boolean USE_OLD_TAX = false;

    private static final BigDecimal ANNUAL_MONTHS_DISCOUNT = new BigDecimal("0.15");
    private static final BigDecimal WELCOME_DISCOUNT = new BigDecimal("0.10");
    private static final BigDecimal BLACK_FRIDAY_DISCOUNT = new BigDecimal("0.30");
    private static final Money FIXED_COUPON_VALUE = Money.of("20");
    private static final BigDecimal LOYALTY_DISCOUNT = new BigDecimal("0.05");
    private static final BigDecimal BR_TAX_RATE = new BigDecimal("0.05");

    public String processBilling(BillingCommand command) {
        Money total = Money.ZERO;
        try {
            Connection conn = DriverManager.getConnection(
                    "jdbc:postgresql://db.billingco.internal:5432/billing",
                    System.getenv("DB_USER"), System.getenv("DB_PASSWORD"));
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT name, signup_date, active FROM customers WHERE id = ?");
            ps.setString(1, command.customerId());
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                return "ERROR: customer not found";
            }
            String name = rs.getString("name");
            LocalDate signupDate = rs.getDate("signup_date").toLocalDate();
            boolean active = rs.getBoolean("active");
            if (!active) {
                return "ERROR: customer inactive";
            }

            // calcula o preço do plano
            Plan plan = command.plan();
            Money base = plan.basePrice();
            if (command.seats() > plan.includedSeats()) {
                base = base.plus(plan.extraSeatPrice().times(command.seats() - plan.includedSeats()));
            }

            // plano anual: 12 meses com 15% de desconto
            if (command.cycle() == BillingCycle.ANNUAL) {
                base = base.times(12).discount(ANNUAL_MONTHS_DISCOUNT);
            }

            LocalDate today = LocalDate.now();

            // cupons
            if (command.hasCoupon()) {
                String coupon = command.couponCode();
                if (coupon.equals("WELCOME10")) {
                    if (ChronoUnit.DAYS.between(signupDate, today) <= 30) {
                        base = base.discount(WELCOME_DISCOUNT);
                    }
                } else if (coupon.equals("BLACKFRIDAY")) {
                    if (today.getMonth() == Month.NOVEMBER) {
                        base = base.discount(BLACK_FRIDAY_DISCOUNT);
                    }
                } else if (coupon.equals("FIXED20")) {
                    base = base.minus(FIXED_COUPON_VALUE).atLeastZero();
                }
            }

            // fidelidade: clientes com 2 anos ou mais ganham 5%
            if (ChronoUnit.YEARS.between(signupDate, today) >= 2) {
                base = base.discount(LOYALTY_DISCOUNT);
            }

            // imposto
            Money tax;
            if (USE_OLD_TAX) {
                tax = base.times(new BigDecimal("0.02"));
            } else {
                if ("BR".equals(command.country())) {
                    tax = base.times(BR_TAX_RATE);
                } else {
                    tax = Money.ZERO;
                }
            }
            total = base.plus(tax).rounded();

            // cobrança no cartão
            URL url = new URL("https://api.payments.billingco.com/v1/charges");
            HttpURLConnection http = (HttpURLConnection) url.openConnection();
            http.setRequestMethod("POST");
            http.setDoOutput(true);
            http.setRequestProperty("Content-Type", "application/json");
            String json = "{\"token\":\"" + command.cardToken() + "\",\"amount\":" + total + ",\"currency\":\"BRL\"}";
            try (OutputStream os = http.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
            int code = http.getResponseCode();
            if (code != 200) {
                return "ERROR: payment declined";
            }
            BufferedReader in = new BufferedReader(new InputStreamReader(http.getInputStream()));
            String transactionId = in.readLine();
            in.close();

            // grava a fatura
            PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO invoices (customer_id, amount, transaction_id, issued_at) VALUES (?, ?, ?, ?)");
            ins.setString(1, command.customerId());
            ins.setBigDecimal(2, total.amount());
            ins.setString(3, transactionId);
            ins.setDate(4, java.sql.Date.valueOf(LocalDate.now()));
            ins.executeUpdate();
            conn.close();

            // envia o recibo por e-mail
            try {
                Socket s = new Socket("smtp.billingco.internal", 25);
                PrintWriter out = new PrintWriter(s.getOutputStream(), true);
                out.println("HELO billingco.com");
                out.println("MAIL FROM:<billing@billingco.com>");
                out.println("RCPT TO:<" + command.email() + ">");
                out.println("DATA");
                out.println("Subject: Recibo SaaS Billing Co.");
                out.println();
                out.println("Ola " + name + ", recebemos o pagamento de R$ " + total + ". Transacao: " + transactionId);
                out.println(".");
                out.println("QUIT");
                s.close();
            } catch (Exception e) {
                // ignora, e-mail nao e importante
            }

            return "SUCCESS: " + total;
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR";
        }
    }

    // usado na versão 1 do sistema
    private double calculateLegacyDiscount(double value) {
        return value * 0.9;
    }

    // TODO remover depois da migração
    private double oldTaxRate(String country) {
        if (country.equals("BR")) {
            return 0.02;
        }
        return 0;
    }
}
