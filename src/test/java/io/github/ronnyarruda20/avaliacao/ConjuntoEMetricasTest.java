package io.github.ronnyarruda20.avaliacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ronnyarruda20.avaliacao.alvo.RespostaRag;
import io.github.ronnyarruda20.avaliacao.alvo.RespostaRag.Fonte;
import io.github.ronnyarruda20.avaliacao.conjunto.Conjunto;
import io.github.ronnyarruda20.avaliacao.conjunto.Conjunto.Caso;
import io.github.ronnyarruda20.avaliacao.metricas.Deterministicas;

class ConjuntoEMetricasTest {

    static final Caso CASO = new Caso("ajuda", List.of(), "Quanto é a ajuda?", "regulamento.pdf", List.of(2),
            List.of("R$ 150,00"), false);

    @Test
    void oConjuntoDoRepositorioEValido() {
        var c = Conjunto.ler(Path.of("avaliacao/conjunto.yaml"));

        assertThat(c.casos()).hasSizeGreaterThanOrEqualTo(10);
        assertThat(c.casos()).filteredOn(Caso::deveRecusar).isNotEmpty();
        assertThat(c.casos()).filteredOn(k -> !k.antes().isEmpty()).isNotEmpty();
        c.documentos().forEach(d -> assertThat(Path.of("avaliacao/documentos", d)).exists());
    }

    @Test
    void recusaConjuntoIncoerente(@TempDir Path pasta) throws Exception {
        Path yaml = pasta.resolve("c.yaml");
        Files.writeString(yaml, """
                nome: x
                limites: {recuperacao: 1, citacao: 1, correcao: 1, fidelidade: 1, recusa: 1}
                casos:
                  - id: a
                    pergunta: fora do escopo
                    deveRecusar: true
                    fatos: [algo]
                """);

        assertThatThrownBy(() -> Conjunto.ler(yaml)).hasMessageContaining("não pode ter fatos");
    }

    @Test
    void recuperacaoECitacaoOlhamDocumentoEPagina() {
        var certo = new Fonte(1, "regulamento.pdf", 2, "R$ 150,00", 0.8);
        var errado = new Fonte(2, "regulamento.pdf", 1, "90 dias", 0.6);

        var citaCerto = new RespostaRag("É R$ 150,00 [1].", List.of(certo, errado), false);
        var citaErrado = new RespostaRag("É R$ 150,00 [2].", List.of(certo, errado), false);
        var buscaErrada = new RespostaRag("É R$ 150,00 [1].", List.of(errado), false);

        assertThat(Deterministicas.recuperou(CASO, citaCerto)).isTrue();
        assertThat(Deterministicas.citouCerto(CASO, citaCerto)).isTrue();
        assertThat(Deterministicas.citouCerto(CASO, citaErrado)).isFalse();
        assertThat(Deterministicas.recuperou(CASO, buscaErrada)).isFalse();
    }

    @Test
    void apontaCitacaoDeFonteQueNaoExiste() {
        var r = new RespostaRag("Algo [1] e outra coisa [7].", List.of(new Fonte(1, "a.pdf", 1, "x", 0.5)), false);

        assertThat(Deterministicas.citacoesInexistentes(r)).containsExactly(7);
    }

    @Test
    void documentoSemPaginasCasaSoPeloNome() {
        var md = new Caso("f", List.of(), "p", "ferias.md", List.of(), List.of("x"), false);
        var r = new RespostaRag("x [1]", List.of(new Fonte(1, "ferias.md", null, "x", 0.7)), false);

        assertThat(Deterministicas.recuperou(md, r)).isTrue();
        assertThat(Deterministicas.citouCerto(md, r)).isTrue();
    }
}
