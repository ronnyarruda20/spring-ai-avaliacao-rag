package io.github.ronnyarruda20.avaliacao.juiz;

import java.util.List;

import io.github.ronnyarruda20.avaliacao.alvo.RespostaRag;
import io.github.ronnyarruda20.avaliacao.conjunto.Conjunto.Caso;

/** Avalia o que só um leitor consegue avaliar: se a resposta está certa e se é fiel aos trechos. */
public interface Juiz {

    Veredito avaliar(Caso caso, RespostaRag resposta);

    /**
     * @param fatos                 cada fato esperado e se a resposta o afirma
     * @param fidelidade            de 0 a 1: fração das afirmações da resposta que os trechos sustentam
     * @param afirmacoesSemSuporte  afirmações que não estão nos trechos
     * @param recusou               a resposta diz que não encontrou a informação
     */
    record Veredito(List<FatoAvaliado> fatos, double fidelidade, List<String> afirmacoesSemSuporte, boolean recusou,
            String comentario) {

        public Veredito {
            fatos = fatos == null ? List.of() : fatos;
            afirmacoesSemSuporte = afirmacoesSemSuporte == null ? List.of() : afirmacoesSemSuporte;
        }
    }

    record FatoAvaliado(String fato, boolean presente, String justificativa) {
    }
}
