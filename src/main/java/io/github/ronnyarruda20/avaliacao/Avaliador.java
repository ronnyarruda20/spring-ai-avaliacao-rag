package io.github.ronnyarruda20.avaliacao;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import io.github.ronnyarruda20.avaliacao.alvo.RespostaRag;
import io.github.ronnyarruda20.avaliacao.alvo.SistemaRag;
import io.github.ronnyarruda20.avaliacao.conjunto.Conjunto;
import io.github.ronnyarruda20.avaliacao.conjunto.Conjunto.Caso;
import io.github.ronnyarruda20.avaliacao.juiz.Juiz;
import io.github.ronnyarruda20.avaliacao.juiz.Juiz.Veredito;
import io.github.ronnyarruda20.avaliacao.metricas.Deterministicas;

/**
 * Roda o conjunto contra o sistema: indexa os documentos, faz cada pergunta numa conversa
 * nova, mede e compara as médias com os limites.
 */
public class Avaliador {

    /**
     * Resultado de um caso. Métricas que não se aplicam ao caso ficam nulas: recuperação,
     * citação, correção e fidelidade valem para perguntas com resposta; recusa, para as sem.
     */
    public record ResultadoCaso(String id, String pergunta, String resposta, Boolean recuperou, Boolean citou,
            List<Integer> citacoesInexistentes, Double correcao, Double fidelidade, Boolean recusou, Veredito veredito,
            String erro, long duracaoMs) {

        public boolean aprovado() {
            return erro == null && !Boolean.FALSE.equals(recuperou) && !Boolean.FALSE.equals(citou)
                    && (correcao == null || correcao == 1.0) && (fidelidade == null || fidelidade >= 0.99)
                    && !Boolean.FALSE.equals(recusou) && citacoesInexistentes.isEmpty();
        }
    }

    public record Metrica(String nome, Double media, double limite, int casos) {

        public boolean aprovada() {
            return media != null && media >= limite;
        }
    }

    public record Resultado(String conjunto, List<ResultadoCaso> casos, List<Metrica> metricas) {

        public boolean aprovado() {
            return metricas.stream().allMatch(Metrica::aprovada) && casos.stream().noneMatch(c -> c.erro() != null);
        }
    }

    private final SistemaRag sistema;
    private final Juiz juiz;

    public Avaliador(SistemaRag sistema, Juiz juiz) {
        this.sistema = sistema;
        this.juiz = juiz;
    }

    public Resultado avaliar(Conjunto conjunto, Path pastaDocumentos, Consumer<ResultadoCaso> progresso) {
        conjunto.documentos().forEach(d -> sistema.indexar(pastaDocumentos.resolve(d)));
        List<ResultadoCaso> resultados = new ArrayList<>();
        for (Caso caso : conjunto.casos()) {
            ResultadoCaso r = avaliarCaso(caso);
            resultados.add(r);
            progresso.accept(r);
        }
        var l = conjunto.limites();
        List<Metrica> metricas = List.of(
                metrica("recuperação", resultados, r -> nota(r.recuperou()), l.recuperacao()),
                metrica("citação", resultados, r -> nota(r.citou()), l.citacao()),
                metrica("correção", resultados, ResultadoCaso::correcao, l.correcao()),
                metrica("fidelidade", resultados, ResultadoCaso::fidelidade, l.fidelidade()),
                metrica("recusa", resultados, r -> nota(r.recusou()), l.recusa()));
        return new Resultado(conjunto.nome(), resultados, metricas);
    }

    ResultadoCaso avaliarCaso(Caso caso) {
        long inicio = System.currentTimeMillis();
        String conversa = "avaliacao-" + caso.id() + "-" + UUID.randomUUID().toString().substring(0, 8);
        try {
            for (String anterior : caso.antes()) {
                sistema.perguntar(anterior, conversa);
            }
            RespostaRag resposta = sistema.perguntar(caso.pergunta(), conversa);
            if (resposta.erro()) {
                return falha(caso, resposta.texto(), "O sistema devolveu erro: " + resposta.texto(), inicio);
            }
            Veredito v = juiz.avaliar(caso, resposta);
            long duracao = System.currentTimeMillis() - inicio;
            if (caso.deveRecusar()) {
                return new ResultadoCaso(caso.id(), caso.pergunta(), resposta.texto(), null, null,
                        Deterministicas.citacoesInexistentes(resposta), null, null, v.recusou(), v, null, duracao);
            }
            double presentes = v.fatos().stream().filter(Juiz.FatoAvaliado::presente).count();
            double correcao = v.recusou() ? 0 : presentes / caso.fatos().size();
            return new ResultadoCaso(caso.id(), caso.pergunta(), resposta.texto(),
                    Deterministicas.recuperou(caso, resposta), Deterministicas.citouCerto(caso, resposta),
                    Deterministicas.citacoesInexistentes(resposta), correcao, v.fidelidade(), null, v, null, duracao);
        } catch (RuntimeException e) {
            return falha(caso, null, e.getMessage(), inicio);
        }
    }

    private static ResultadoCaso falha(Caso caso, String resposta, String erro, long inicio) {
        return new ResultadoCaso(caso.id(), caso.pergunta(), resposta, null, null, List.of(), null, null, null, null, erro,
                System.currentTimeMillis() - inicio);
    }

    private static Metrica metrica(String nome, List<ResultadoCaso> rs, Function<ResultadoCaso, Double> valor,
            double limite) {
        List<Double> valores = rs.stream().map(valor).filter(v -> v != null).toList();
        OptionalDouble media = valores.stream().mapToDouble(Double::doubleValue).average();
        return new Metrica(nome, media.isPresent() ? media.getAsDouble() : null, limite, valores.size());
    }

    private static Double nota(Boolean b) {
        return b == null ? null : b ? 1.0 : 0.0;
    }
}
