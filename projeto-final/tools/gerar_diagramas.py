"""Gera os diagramas C4 (níveis 1 e 2) da VelaPay em SVG, sem dependências externas.

Uso: python3 projeto-final/tools/gerar_diagramas.py
"""
from pathlib import Path
from xml.sax.saxutils import escape

OUT = Path(__file__).resolve().parent.parent / "diagramas"

COR = {
    "pessoa": "#08427b",
    "sistema": "#1168bd",
    "conteiner": "#438dd5",
    "externo": "#8a8a8a",
    "seta": "#555555",
}
FONTE = "DejaVu Sans, Arial, sans-serif"


class Svg:
    def __init__(self, w, h, titulo):
        self.w, self.h = w, h
        self.p = [
            f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" '
            f'viewBox="0 0 {w} {h}" font-family="{FONTE}">',
            "<defs><marker id='a' viewBox='0 0 10 10' refX='9' refY='5' markerWidth='8' "
            "markerHeight='8' orient='auto-start-reverse'><path d='M0,0 L10,5 L0,10 z' "
            f"fill='{COR['seta']}'/></marker></defs>",
            f'<rect width="{w}" height="{h}" fill="#ffffff"/>',
            f'<text x="20" y="34" font-size="22" font-weight="bold" fill="#222">{escape(titulo)}</text>',
        ]
        self.caixas = {}

    def _textos(self, cx, y, linhas, cor="#fff"):
        for tam, peso, txt in linhas:
            y += tam + 4
            self.p.append(
                f'<text x="{cx}" y="{y}" font-size="{tam}" font-weight="{peso}" '
                f'text-anchor="middle" fill="{cor}">{escape(txt)}</text>'
            )

    def caixa(self, chave, cx, cy, w, h, cor, nome, tipo, desc, tracejada=False):
        x, y = cx - w / 2, cy - h / 2
        borda = ' stroke-dasharray="8 5"' if tracejada else ""
        self.p.append(
            f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="10" fill="{cor}" '
            f'stroke="#2a2a2a" stroke-opacity="0.35"{borda}/>'
        )
        linhas = [(16, "bold", nome), (12, "normal", tipo)] + [(12, "normal", d) for d in desc]
        alt = sum(t + 4 for t, _, _ in linhas) + 6
        self._textos(cx, cy - alt / 2, linhas[:2])
        self._textos(cx, cy - alt / 2 + 42, linhas[2:])
        self.caixas[chave] = (cx, cy, w, h)

    def pessoa(self, chave, cx, cy, nome, desc):
        w, h = 250, 110
        self.p.append(f'<circle cx="{cx}" cy="{cy - h/2 - 22}" r="24" fill="{COR["pessoa"]}"/>')
        self.p.append(
            f'<rect x="{cx - w/2}" y="{cy - h/2}" width="{w}" height="{h}" rx="28" fill="{COR["pessoa"]}"/>'
        )
        self._textos(cx, cy - h / 2 + 8, [(16, "bold", nome), (12, "normal", "[Pessoa]")])
        self._textos(cx, cy - h / 2 + 50, [(12, "normal", d) for d in desc])
        self.caixas[chave] = (cx, cy - 12, w, h + 46)

    def banco(self, chave, cx, cy, w, h, nome, tipo, desc):
        x, y, e = cx - w / 2, cy - h / 2, 14
        c = COR["conteiner"]
        self.p.append(
            f'<path d="M{x},{y+e} A{w/2},{e} 0 0 0 {x+w},{y+e} L{x+w},{y+h-e} '
            f'A{w/2},{e} 0 0 1 {x},{y+h-e} Z" fill="{c}" stroke="#2a2a2a" stroke-opacity="0.35"/>'
        )
        self.p.append(
            f'<ellipse cx="{cx}" cy="{y+e}" rx="{w/2}" ry="{e}" fill="#5a9fe0" '
            f'stroke="#2a2a2a" stroke-opacity="0.35"/>'
        )
        self._textos(cx, y + 2 * e, [(16, "bold", nome), (12, "normal", tipo)])
        self._textos(cx, y + 2 * e + 42, [(12, "normal", d) for d in desc])
        self.caixas[chave] = (cx, cy, w, h)

    def fronteira(self, x, y, w, h, rotulo):
        self.p.append(
            f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="6" fill="none" '
            f'stroke="#666" stroke-width="2" stroke-dasharray="10 6"/>'
        )
        self.p.append(
            f'<text x="{x+14}" y="{y+h-14}" font-size="14" font-weight="bold" fill="#444">{escape(rotulo)}</text>'
        )

    def _borda(self, chave, dx, dy):
        cx, cy, w, h = self.caixas[chave]
        if dx == 0 and dy == 0:
            return cx, cy
        tx = (w / 2) / abs(dx) if dx else float("inf")
        ty = (h / 2) / abs(dy) if dy else float("inf")
        t = min(tx, ty)
        return cx + dx * t, cy + dy * t

    def seta(self, de, para, rotulo, tec=None, dupla=False, desl=(0, 0), rot_desl=(0, 0)):
        (ax, ay, _, _), (bx, by, _, _) = self.caixas[de], self.caixas[para]
        ax2, ay2 = ax + desl[0], ay + desl[1]
        bx2, by2 = bx + desl[0], by + desl[1]
        dx, dy = bx2 - ax2, by2 - ay2
        self.caixas["_a"] = (ax2, ay2) + self.caixas[de][2:]
        self.caixas["_b"] = (bx2, by2) + self.caixas[para][2:]
        x1, y1 = self._borda("_a", dx, dy)
        x2, y2 = self._borda("_b", -dx, -dy)
        inicio = ' marker-start="url(#a)"' if dupla else ""
        self.p.append(
            f'<line x1="{x1:.1f}" y1="{y1:.1f}" x2="{x2:.1f}" y2="{y2:.1f}" stroke="{COR["seta"]}" '
            f'stroke-width="1.6" stroke-dasharray="6 4" marker-end="url(#a)"{inicio}/>'
        )
        mx, my = (x1 + x2) / 2 + rot_desl[0], (y1 + y2) / 2 + rot_desl[1]
        linhas = [rotulo] + ([f"[{tec}]"] if tec else [])
        larg = max(len(l) for l in linhas) * 6.6 + 12
        alt = 17 * len(linhas) + 6
        self.p.append(
            f'<rect x="{mx - larg/2:.1f}" y="{my - alt/2:.1f}" width="{larg:.1f}" height="{alt}" '
            f'rx="4" fill="#ffffff" fill-opacity="0.92"/>'
        )
        for i, l in enumerate(linhas):
            estilo = 'font-style="italic" fill="#555"' if i else 'fill="#222"'
            self.p.append(
                f'<text x="{mx:.1f}" y="{my - alt/2 + 16 + 17*i:.1f}" font-size="12" '
                f'text-anchor="middle" {estilo}>{escape(l)}</text>'
            )

    def legenda(self, x, y, itens):
        self.p.append(f'<text x="{x}" y="{y}" font-size="13" font-weight="bold" fill="#333">Legenda</text>')
        for i, (cor, txt) in enumerate(itens):
            yy = y + 12 + i * 24
            self.p.append(f'<rect x="{x}" y="{yy}" width="18" height="16" rx="3" fill="{cor}"/>')
            self.p.append(f'<text x="{x+26}" y="{yy+13}" font-size="12" fill="#333">{escape(txt)}</text>')

    def salvar(self, nome):
        OUT.mkdir(parents=True, exist_ok=True)
        (OUT / nome).write_text("\n".join(self.p + ["</svg>"]), encoding="utf-8")


def contexto():
    s = Svg(1300, 900, "C4 Nível 1 — Diagrama de Contexto: VelaPay")
    s.pessoa("cli", 300, 170, "Cliente", ["Pessoa física que mantém saldo,", "transfere e paga contas"])
    s.pessoa("op", 1000, 170, "Operador de Suporte", ["Atende contestações e", "consulta trilha de auditoria"])
    s.caixa("vp", 650, 470, 380, 150, COR["sistema"], "VelaPay", "[Sistema de software]",
            ["Carteira digital: conta, saldo, extrato,", "transferências, pagamentos e notificações"])
    s.caixa("psp", 300, 760, 340, 120, COR["externo"], "PSP / Banco Parceiro", "[Sistema externo]",
            ["Pix (cash-in/cash-out), boleto", "e liquidação financeira"])
    s.caixa("ntf", 1000, 760, 340, 120, COR["externo"], "Provedores de Notificação", "[Sistemas externos]",
            ["Push (FCM/APNs), e-mail e SMS"])
    s.seta("cli", "vp", "Consulta saldo, transfere e paga", "HTTPS / app móvel")
    s.seta("op", "vp", "Consulta transações e auditoria", "HTTPS / navegador")
    s.seta("vp", "psp", "Envia ordens de pagamento", "HTTPS / API do PSP", desl=(-40, 0), rot_desl=(-95, 0))
    s.seta("psp", "vp", "Confirma pagamentos e depósitos", "Webhooks HTTPS assinados", desl=(40, 0), rot_desl=(95, 0))
    s.seta("vp", "ntf", "Envia push, e-mail e SMS ao cliente", "HTTPS")
    s.legenda(1060, 420, [(COR["pessoa"], "Pessoa"), (COR["sistema"], "Sistema em escopo"),
                         (COR["externo"], "Sistema externo")])
    s.salvar("c4-contexto.svg")


def conteineres():
    s = Svg(1500, 1260, "C4 Nível 2 — Diagrama de Contêineres: VelaPay")
    s.pessoa("cli", 220, 140, "Cliente", ["Usa a carteira pelo celular"])
    s.pessoa("op", 1220, 140, "Operador de Suporte", ["Usa o back-office web"])
    s.fronteira(30, 245, 1440, 830, "VelaPay [Sistema de software]")

    W, H = 300, 120
    s.caixa("app", 220, 340, W, H, COR["conteiner"], "App Móvel", "[Contêiner: Flutter]",
            ["Interface do cliente;", "gera a Idempotency-Key por operação"])
    s.caixa("idp", 720, 340, W, H, COR["conteiner"], "Provedor de Identidade", "[Contêiner: Keycloak]",
            ["OIDC/OAuth 2.0, MFA,", "step-up para transações sensíveis"])
    s.caixa("bo", 1220, 340, W, H, COR["conteiner"], "Back-office Web", "[Contêiner: SPA React]",
            ["Consulta de transações,", "contestações e auditoria"])
    s.caixa("gw", 720, 520, W, H, COR["conteiner"], "API Gateway", "[Contêiner: Kong]",
            ["TLS, validação de JWT,", "rate limiting, correlation-id"])
    s.caixa("core", 720, 720, 380, 140, COR["conteiner"], "Wallet Core", "[Contêiner: Java 21 / Spring Boot]",
            ["Monólito modular hexagonal: Contas,", "Ledger, Transferências, Pagamentos,", "Limites; grava Outbox"])
    s.banco("db", 195, 720, 300, 150, "Banco do Ledger", "[Contêiner: PostgreSQL 16]",
            ["Contas, lançamentos, idempotência,", "outbox; primário + réplica síncrona"])
    s.caixa("mq", 1245, 720, W, H, COR["conteiner"], "Message Broker", "[Contêiner: RabbitMQ]",
            ["Filas de eventos e comandos,", "quorum queues e DLQ"])
    s.caixa("piw", 720, 940, 340, 120, COR["conteiner"], "Payment Integration Worker",
            "[Contêiner: Java 21 / Resilience4j]",
            ["Camada anticorrupção do PSP: retry,", "circuit breaker, webhooks"])
    s.banco("idb", 220, 940, 300, 130, "Banco da Integração", "[Contêiner: PostgreSQL 16]",
            ["Tentativas, respostas do PSP", "e eventos recebidos (inbox)"])
    s.caixa("ns", 1245, 940, W, H, COR["conteiner"], "Notification Service", "[Contêiner: Node.js]",
            ["Consome eventos e envia", "push, e-mail e SMS"])

    s.caixa("psp", 720, 1170, 340, 100, COR["externo"], "PSP / Banco Parceiro", "[Sistema externo]",
            ["Pix, boleto e liquidação"])
    s.caixa("ntf", 1245, 1170, 300, 100, COR["externo"], "Provedores de Notificação", "[Sistemas externos]",
            ["FCM/APNs, Amazon SES, SMS"])

    s.seta("cli", "app", "Usa")
    s.seta("op", "bo", "Usa")
    s.seta("app", "idp", "Autentica (login, MFA)", "OIDC/HTTPS")
    s.seta("bo", "idp", "Autentica", "OIDC/HTTPS")
    s.seta("app", "gw", "Chamadas de API", "JSON/HTTPS", rot_desl=(-40, 0))
    s.seta("bo", "gw", "Chamadas de API", "JSON/HTTPS", rot_desl=(40, 0))
    s.seta("gw", "idp", "Valida tokens (JWKS)", rot_desl=(0, 0))
    s.seta("gw", "core", "Encaminha requisições", "JSON/HTTPS")
    s.seta("core", "db", "Lê e grava", "JDBC, ACID")
    s.seta("core", "mq", "Publica / consome", "AMQP", dupla=True)
    s.seta("mq", "piw", "Ordens e resultados de pagamento", "AMQP", dupla=True, rot_desl=(-60, 0))
    s.seta("mq", "ns", "Entrega eventos de domínio", "AMQP")
    s.seta("piw", "idb", "Lê e grava", "JDBC")
    s.seta("piw", "psp", "Ordens de pagamento / webhooks", "HTTPS + mTLS", dupla=True)
    s.seta("ns", "ntf", "Envia notificações", "HTTPS")
    s.legenda(40, 1150, [(COR["pessoa"], "Pessoa"), (COR["conteiner"], "Contêiner da VelaPay"),
                        (COR["externo"], "Sistema externo")])
    s.salvar("c4-conteineres.svg")


if __name__ == "__main__":
    contexto()
    conteineres()
    print("Diagramas gerados em", OUT)
