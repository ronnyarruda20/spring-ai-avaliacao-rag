package io.github.ronnyarruda20.avaliacao.conjunto;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Conjunto de avaliação lido de um YAML: documentos a indexar, limites de qualidade e casos.
 */
public record Conjunto(String nome, List<String> documentos, Limites limites, List<Caso> casos) {

    /** Média mínima de cada métrica para a avaliação passar. */
    public record Limites(double recuperacao, double citacao, double correcao, double fidelidade, double recusa) {
    }

    /**
     * @param antes        perguntas feitas antes, na mesma conversa (para testar continuação)
     * @param documento    onde a resposta está; vazio em casos de recusa
     * @param paginas      páginas onde a resposta está; vazio para documentos sem paginação
     * @param fatos        o que a resposta precisa afirmar
     * @param deveRecusar  a resposta não está nos documentos, e o sistema deve dizer isso
     */
    public record Caso(String id, List<String> antes, String pergunta, String documento, List<Integer> paginas,
            List<String> fatos, Boolean deveRecusar) {

        public Caso {
            antes = antes == null ? List.of() : antes;
            paginas = paginas == null ? List.of() : paginas;
            fatos = fatos == null ? List.of() : fatos;
            deveRecusar = Boolean.TRUE.equals(deveRecusar);
        }
    }

    public Conjunto {
        documentos = documentos == null ? List.of() : documentos;
        casos = casos == null ? List.of() : casos;
    }

    public static Conjunto ler(Path arquivo) {
        try {
            Conjunto c = YAMLMapper.builder()
                    .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .build()
                    .readValue(Files.readString(arquivo), Conjunto.class);
            c.validar();
            return c;
        } catch (IOException e) {
            throw new UncheckedIOException("Não consegui ler o conjunto " + arquivo, e);
        }
    }

    void validar() {
        if (limites == null) {
            throw new IllegalArgumentException("O conjunto precisa definir os limites.");
        }
        var ids = new java.util.HashSet<String>();
        for (Caso c : casos) {
            if (!ids.add(c.id())) {
                throw new IllegalArgumentException("Caso repetido: " + c.id());
            }
            if (c.deveRecusar() && !c.fatos().isEmpty()) {
                throw new IllegalArgumentException("Caso " + c.id() + " deve recusar, então não pode ter fatos.");
            }
            if (!c.deveRecusar() && (c.fatos().isEmpty() || c.documento() == null)) {
                throw new IllegalArgumentException("Caso " + c.id() + " precisa de documento e fatos.");
            }
        }
    }
}
