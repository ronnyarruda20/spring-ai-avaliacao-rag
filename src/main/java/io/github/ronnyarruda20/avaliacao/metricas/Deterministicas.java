package io.github.ronnyarruda20.avaliacao.metricas;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.ronnyarruda20.avaliacao.alvo.RespostaRag;
import io.github.ronnyarruda20.avaliacao.alvo.RespostaRag.Fonte;
import io.github.ronnyarruda20.avaliacao.conjunto.Conjunto.Caso;

/**
 * Métricas que não dependem de modelo: são baratas, rápidas e dão sempre o mesmo resultado.
 * Rodam antes do avaliador e já apontam onde está o problema, na busca ou na citação.
 */
public final class Deterministicas {

    private static final Pattern CITACAO = Pattern.compile("\\[(\\d{1,3})]");

    private Deterministicas() {
    }

    /** A busca trouxe algum trecho do lugar certo (documento e, se houver, página)? */
    public static boolean recuperou(Caso caso, RespostaRag resposta) {
        return resposta.fontes().stream().anyMatch(f -> doLugarCerto(caso, f));
    }

    /** A resposta cita pelo menos um trecho do lugar certo? */
    public static boolean citouCerto(Caso caso, RespostaRag resposta) {
        return citadas(resposta.texto()).stream()
                .map(i -> fonte(resposta, i))
                .anyMatch(f -> f != null && doLugarCerto(caso, f));
    }

    /** Citações a números que não existem entre as fontes: sinal de invenção. */
    public static List<Integer> citacoesInexistentes(RespostaRag resposta) {
        return citadas(resposta.texto()).stream().filter(i -> fonte(resposta, i) == null).toList();
    }

    static Set<Integer> citadas(String texto) {
        Set<Integer> numeros = new LinkedHashSet<>();
        Matcher m = CITACAO.matcher(texto == null ? "" : texto);
        while (m.find()) {
            numeros.add(Integer.parseInt(m.group(1)));
        }
        return numeros;
    }

    private static Fonte fonte(RespostaRag resposta, int indice) {
        return resposta.fontes().stream().filter(f -> f.indice() == indice).findFirst().orElse(null);
    }

    private static boolean doLugarCerto(Caso caso, Fonte f) {
        if (!f.documentoNome().equals(caso.documento())) {
            return false;
        }
        return caso.paginas().isEmpty() || (f.pagina() != null && caso.paginas().contains(f.pagina()));
    }
}
