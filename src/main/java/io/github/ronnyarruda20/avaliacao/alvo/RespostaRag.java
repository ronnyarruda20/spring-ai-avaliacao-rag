package io.github.ronnyarruda20.avaliacao.alvo;

import java.util.List;

/** O que o sistema avaliado respondeu: o texto e os trechos que ele disse ter usado. */
public record RespostaRag(String texto, List<Fonte> fontes, boolean erro) {

    public record Fonte(int indice, String documentoNome, Integer pagina, String texto, double similaridade) {
    }
}
