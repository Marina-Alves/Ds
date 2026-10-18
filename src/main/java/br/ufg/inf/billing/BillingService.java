package br.ufg.inf.billing;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
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

    public String processBilling(String customerId, String plan, int seats, String coupon,
                                 boolean annual, String cardToken, String email, String country) {
        double total = 0;
        try {
            Connection conn = DriverManager.getConnection(
                    "jdbc:postgresql://db.billingco.internal:5432/billing",
                    System.getenv("DB_USER"), System.getenv("DB_PASSWORD"));
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT name, signup_date, active FROM customers WHERE id = ?");
            ps.setString(1, customerId);
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
            double base;
            if (plan.equals("BASIC")) {
                base = 29.90;
                if (seats > 1) {
                    base = base + (seats - 1) * 9.90;
                }
            } else if (plan.equals("PRO")) {
                base = 99.90;
                if (seats > 5) {
                    base = base + (seats - 5) * 15.0;
                }
            } else if (plan.equals("ENTERPRISE")) {
                base = 499.90;
                if (seats > 20) {
                    base = base + (seats - 20) * 10.0;
                }
            } else {
                return "ERROR: invalid plan";
            }

            // plano anual: 12 meses com 15% de desconto
            if (annual) {
                base = base * 12;
                base = base - base * 0.15;
            }

            LocalDate today = LocalDate.now();

            // cupons
            if (coupon != null && !coupon.isEmpty()) {
                if (coupon.equals("WELCOME10")) {
                    if (ChronoUnit.DAYS.between(signupDate, today) <= 30) {
                        base = base - base * 0.10;
                    }
                } else if (coupon.equals("BLACKFRIDAY")) {
                    if (today.getMonth() == Month.NOVEMBER) {
                        base = base - base * 0.30;
                    }
                } else if (coupon.equals("FIXED20")) {
                    base = base - 20;
                    if (base < 0) {
                        base = 0;
                    }
                }
            }

            // fidelidade: clientes com 2 anos ou mais ganham 5%
            if (ChronoUnit.YEARS.between(signupDate, today) >= 2) {
                base = base - base * 0.05;
            }

            // imposto
            double tax;
            if (USE_OLD_TAX) {
                tax = base * 0.02;
            } else {
                if (country != null && country.equals("BR")) {
                    tax = base * 0.05;
                } else {
                    tax = 0;
                }
            }
            total = base + tax;
            total = Math.round(total * 100.0) / 100.0;

            // cobrança no cartão
            URL url = new URL("https://api.payments.billingco.com/v1/charges");
            HttpURLConnection http = (HttpURLConnection) url.openConnection();
            http.setRequestMethod("POST");
            http.setDoOutput(true);
            http.setRequestProperty("Content-Type", "application/json");
            String json = "{\"token\":\"" + cardToken + "\",\"amount\":" + total + ",\"currency\":\"BRL\"}";
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
            ins.setString(1, customerId);
            ins.setDouble(2, total);
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
                out.println("RCPT TO:<" + email + ">");
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
