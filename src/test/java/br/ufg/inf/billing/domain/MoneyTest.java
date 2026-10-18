package br.ufg.inf.billing.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyTest {

    @Test
    void somaSemErroDePontoFlutuante() {
        assertEquals(Money.of("0.30"), Money.of("0.10").plus(Money.of("0.20")));
    }

    @Test
    void igualdadeIgnoraEscala() {
        assertEquals(Money.of("10.0"), Money.of("10.00"));
        assertEquals(Money.of("10.0").hashCode(), Money.of("10.00").hashCode());
    }

    @Test
    void arredondaParaCentavosComHalfUp() {
        assertEquals("94.91", Money.of("94.905").rounded().toString());
        assertEquals("94.90", Money.of("94.9049").rounded().toString());
    }

    @Test
    void descontoPercentualPreservaPrecisaoAteOArredondamento() {
        Money price = Money.of("99.90").discount(new BigDecimal("0.05"));
        assertEquals(Money.of("94.905"), price);
    }

    @Test
    void atLeastZeroImpedeValorNegativo() {
        assertEquals(Money.ZERO, Money.of("15").minus(Money.of("20")).atLeastZero());
    }

    @Test
    void operacoesNaoAlteramOValorOriginal() {
        Money original = Money.of("50");
        Money doubled = original.times(2);
        assertNotSame(original, doubled);
        assertEquals(Money.of("50"), original);
        assertEquals(Money.of("100"), doubled);
    }

    @Test
    void naoAceitaValorNulo() {
        assertThrows(NullPointerException.class, () -> new Money(null));
    }
}
