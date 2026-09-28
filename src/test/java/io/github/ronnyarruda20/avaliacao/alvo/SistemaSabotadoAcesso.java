package io.github.ronnyarruda20.avaliacao.alvo;

/** Expõe aos testes de outro pacote a sabotagem determinística. */
public final class SistemaSabotadoAcesso {

    private SistemaSabotadoAcesso() {
    }

    public static RespostaRag sabotar(RespostaRag r, int vez) {
        return SistemaSabotado.sabotar(r, vez);
    }
}
