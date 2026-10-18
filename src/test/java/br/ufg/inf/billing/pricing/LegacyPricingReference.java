package br.ufg.inf.billing.pricing;

import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.ChronoUnit;

/**
 * Cópia literal da aritmética de preço do BillingService legado (commit 1), com o
 * LocalDate.now() trocado por um parâmetro. Serve apenas de oráculo para o teste de
 * caracterização: o código de produção não depende desta classe.
 */
final class LegacyPricingReference {

    private LegacyPricingReference() {
    }

    static double total(String plan, int seats, String coupon, boolean annual, String country,
                        LocalDate signupDate, LocalDate today) {
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
        } else {
            base = 499.90;
            if (seats > 20) {
                base = base + (seats - 20) * 10.0;
            }
        }
        if (annual) {
            base = base * 12;
            base = base - base * 0.15;
        }
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
        if (ChronoUnit.YEARS.between(signupDate, today) >= 2) {
            base = base - base * 0.05;
        }
        double tax = (country != null && country.equals("BR")) ? base * 0.05 : 0;
        double total = base + tax;
        return Math.round(total * 100.0) / 100.0;
    }
}
