package io.github.ronnyarruda20.avaliacao;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Collectors;

import io.github.ronnyarruda20.avaliacao.Avaliador.Metrica;
import io.github.ronnyarruda20.avaliacao.Avaliador.Resultado;
import io.github.ronnyarruda20.avaliacao.Avaliador.ResultadoCaso;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Grava o resultado em Markdown, para ler, e em JSON, para comparar execuções. */
public final class Relatorio {

    private Relatorio() {
    }

    public record Arquivos(Path markdown, Path json) {
    }

    public static Arquivos gravar(Resultado r, String alvo, String juiz, Path pasta, LocalDateTime quando) {
        try {
            Files.createDirectories(pasta);
            String nome = "avaliacao-" + quando.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"));
            Path md = pasta.resolve(nome + ".md");
            Path json = pasta.resolve(nome + ".json");
            Files.writeString(md, markdown(r, alvo, juiz, quando));
            JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build().writeValue(json.toFile(), r);
            return new Arquivos(md, json);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String markdown(Resultado r, String alvo, String juiz, LocalDateTime quando) {
        var sb = new StringBuilder();
        sb.append("# Avaliação: ").append(r.conjunto()).append("\n\n");
        sb.append(r.aprovado() ? "**Resultado: aprovado.**" : "**Resultado: reprovado.**").append("\n\n");
        sb.append("- Sistema avaliado: ").append(alvo).append('\n');
        sb.append("- Modelo avaliador: ").append(juiz).append('\n');
        sb.append("- Data: ").append(quando.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))).append("\n\n");

        sb.append("## Métricas\n\n| Métrica | Média | Limite | Casos | |\n|---|---|---|---|---|\n");
        for (Metrica m : r.metricas()) {
            sb.append("| ").append(m.nome()).append(" | ").append(pct(m.media())).append(" | ").append(pct(m.limite()))
                    .append(" | ").append(m.casos()).append(" | ").append(m.aprovada() ? "✅" : "❌").append(" |\n");
        }

        sb.append("\n## Casos\n\n| Caso | Recuperação | Citação | Correção | Fidelidade | Recusa | |\n|---|---|---|---|---|---|---|\n");
        for (ResultadoCaso c : r.casos()) {
            sb.append("| ").append(c.id()).append(" | ").append(sim(c.recuperou())).append(" | ").append(sim(c.citou()))
                    .append(" | ").append(pct(c.correcao())).append(" | ").append(pct(c.fidelidade())).append(" | ")
                    .append(sim(c.recusou())).append(" | ").append(c.aprovado() ? "✅" : "❌").append(" |\n");
        }

        var falhas = r.casos().stream().filter(c -> !c.aprovado()).toList();
        if (!falhas.isEmpty()) {
            sb.append("\n## O que falhou\n");
            for (ResultadoCaso c : falhas) {
                sb.append("\n### ").append(c.id()).append("\n\n");
                sb.append("**Pergunta:** ").append(c.pergunta()).append("\n\n");
                if (c.erro() != null) {
                    sb.append("**Erro:** ").append(c.erro()).append('\n');
                    continue;
                }
                sb.append("**Resposta:** ").append(c.resposta().replace("\n", " ")).append("\n\n");
                if (Boolean.FALSE.equals(c.recuperou())) {
                    sb.append("- A busca não trouxe nenhum trecho do lugar esperado.\n");
                }
                if (Boolean.FALSE.equals(c.citou())) {
                    sb.append("- A resposta não cita um trecho do lugar esperado.\n");
                }
                if (!c.citacoesInexistentes().isEmpty()) {
                    sb.append("- Cita fontes que não existem: ").append(c.citacoesInexistentes()).append('\n');
                }
                if (c.veredito() != null) {
                    c.veredito().fatos().stream().filter(f -> !f.presente()).forEach(f ->
                            sb.append("- Fato ausente: ").append(f.fato()).append(" (").append(f.justificativa()).append(")\n"));
                    c.veredito().afirmacoesSemSuporte().forEach(a ->
                            sb.append("- Afirmação sem suporte nos trechos: ").append(a).append('\n'));
                    if (Boolean.FALSE.equals(c.recusou())) {
                        sb.append("- Deveria ter dito que não encontrou a informação.\n");
                    }
                    sb.append("- Avaliador: ").append(c.veredito().comentario()).append('\n');
                }
            }
        }
        return sb.toString();
    }

    static String resumo(Resultado r) {
        return r.metricas().stream()
                .map(m -> String.format(Locale.ROOT, "%-12s %6s (limite %s) %s", m.nome(), pct(m.media()), pct(m.limite()),
                        m.aprovada() ? "ok" : "ABAIXO"))
                .collect(Collectors.joining("\n"));
    }

    private static String pct(Double v) {
        return v == null ? "–" : String.format(Locale.ROOT, "%.0f%%", v * 100);
    }

    private static String sim(Boolean b) {
        return b == null ? "–" : b ? "sim" : "não";
    }
}
