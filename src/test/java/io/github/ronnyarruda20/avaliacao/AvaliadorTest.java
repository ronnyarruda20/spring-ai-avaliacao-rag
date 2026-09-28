package io.github.ronnyarruda20.avaliacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.ronnyarruda20.avaliacao.alvo.RespostaRag;
import io.github.ronnyarruda20.avaliacao.alvo.RespostaRag.Fonte;
import io.github.ronnyarruda20.avaliacao.alvo.SistemaRag;
import io.github.ronnyarruda20.avaliacao.conjunto.Conjunto;
import io.github.ronnyarruda20.avaliacao.conjunto.Conjunto.Caso;
import io.github.ronnyarruda20.avaliacao.conjunto.Conjunto.Limites;
import io.github.ronnyarruda20.avaliacao.juiz.Juiz;

class AvaliadorTest {

    static final Fonte PAG2 = new Fonte(1, "regulamento.pdf", 2, "Ajuda de custo de R$ 150,00.", 0.8);

    static final Caso AJUDA = new Caso("ajuda", List.of(), "Quanto é a ajuda?", "regulamento.pdf", List.of(2),
            List.of("R$ 150,00"), false);
    static final Caso HIBRIDO = new Caso("hibrido", List.of("Quanto é a ajuda?"), "E no híbrido?", "regulamento.pdf",
            List.of(2), List.of("R$ 80,00"), false);
    static final Caso VALE = new Caso("vale", List.of(), "E o vale-refeição?", null, List.of(), List.of(), true);

    /** Sistema falso: responde por pergunta e registra as conversas. */
    static class SistemaFalso implements SistemaRag {
        final Map<String, RespostaRag> respostas;
        final List<String> perguntas = new ArrayList<>();
        final List<String> conversas = new ArrayList<>();

        SistemaFalso(Map<String, RespostaRag> respostas) {
            this.respostas = respostas;
        }

        @Override
        public void indexar(Path documento) {
        }

        @Override
        public RespostaRag perguntar(String pergunta, String conversaId) {
            perguntas.add(pergunta);
            conversas.add(conversaId);
            return respostas.getOrDefault(pergunta, new RespostaRag("?", List.of(), false));
        }
    }

    /** Juiz falso: fato presente se o texto dele aparece na resposta; recusa se diz "não encontrei". */
    static final Juiz JUIZ = (caso, r) -> new Juiz.Veredito(
            caso.fatos().stream().map(f -> new Juiz.FatoAvaliado(f, r.texto().contains(f), "")).toList(),
            r.texto().contains("bônus") ? 0.5 : 1.0,
            List.of(), r.texto().contains("Não encontrei"), "ok");

    static Conjunto conjunto(Caso... casos) {
        return new Conjunto("teste", List.of(), new Limites(0.9, 0.9, 0.9, 0.9, 1.0), List.of(casos));
    }

    @Test
    void sistemaBomPassaEMetricasSoContamOsCasosAQueSeAplicam() {
        var sistema = new SistemaFalso(Map.of(
                "Quanto é a ajuda?", new RespostaRag("É R$ 150,00 [1].", List.of(PAG2), false),
                "E no híbrido?", new RespostaRag("É R$ 80,00 [1].", List.of(PAG2), false),
                "E o vale-refeição?", new RespostaRag("Não encontrei isso nos documentos.", List.of(), false)));

        var r = new Avaliador(sistema, JUIZ).avaliar(conjunto(AJUDA, HIBRIDO, VALE), Path.of("."), x -> { });

        assertThat(r.aprovado()).isTrue();
        assertThat(r.metricas()).extracting(Avaliador.Metrica::casos).containsExactly(2, 2, 2, 2, 1);
    }

    @Test
    void perguntaDeContinuacaoUsaAMesmaConversa() {
        var sistema = new SistemaFalso(Map.of());

        new Avaliador(sistema, JUIZ).avaliar(conjunto(HIBRIDO), Path.of("."), x -> { });

        assertThat(sistema.perguntas).containsExactly("Quanto é a ajuda?", "E no híbrido?");
        assertThat(sistema.conversas.get(0)).isEqualTo(sistema.conversas.get(1));
    }

    @Test
    void sistemaQueInventaReprovaNaMetricaCerta() {
        var sistema = new SistemaFalso(Map.of(
                "Quanto é a ajuda?", new RespostaRag("É R$ 150,00 [1]. Há bônus de R$ 2.000 [1].", List.of(PAG2), false),
                "E o vale-refeição?", new RespostaRag("O vale é de R$ 35.", List.of(), false)));

        var r = new Avaliador(sistema, JUIZ).avaliar(conjunto(AJUDA, VALE), Path.of("."), x -> { });

        assertThat(r.aprovado()).isFalse();
        assertThat(r.metricas()).filteredOn(m -> !m.aprovada()).extracting(Avaliador.Metrica::nome)
                .containsExactly("fidelidade", "recusa");
    }

    @Test
    void erroDoSistemaReprovaSemDerrubarAAvaliacao() {
        SistemaRag quebrado = new SistemaFalso(Map.of()) {
            @Override
            public RespostaRag perguntar(String pergunta, String conversaId) {
                throw new IllegalStateException("HTTP 500");
            }
        };

        var r = new Avaliador(quebrado, JUIZ).avaliar(conjunto(AJUDA, VALE), Path.of("."), x -> { });

        assertThat(r.casos()).allSatisfy(c -> assertThat(c.erro()).contains("HTTP 500"));
        assertThat(r.aprovado()).isFalse();
    }

    @Test
    void relatorioExplicaOQueFalhou() {
        var sistema = new SistemaFalso(Map.of(
                "Quanto é a ajuda?", new RespostaRag("É R$ 999 [3].", List.of(PAG2), false)));

        var r = new Avaliador(sistema, JUIZ).avaliar(conjunto(AJUDA), Path.of("."), x -> { });
        String md = Relatorio.markdown(r, "http://alvo", "juiz", LocalDateTime.of(2026, 9, 28, 10, 0));

        assertThat(md).contains("**Resultado: reprovado.**")
                .contains("### ajuda")
                .contains("Fato ausente: R$ 150,00")
                .contains("A resposta não cita um trecho do lugar esperado.")
                .contains("Cita fontes que não existem: [3]");
    }
}
