package io.github.ronnyarruda20.avaliacao.alvo;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Fala com a API do spring-ai-chat-documentos pelo HTTP, como qualquer cliente faria.
 * O avaliador não conhece o código do sistema avaliado.
 */
public class ClienteChatDocumentos implements SistemaRag {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    record Documento(String id, String nome) {
    }

    private final String base;
    private final RestClient rest;
    private final HttpClient http;
    private final Duration timeout;

    public ClienteChatDocumentos(String base, Duration timeout) {
        this.base = base.replaceAll("/+$", "");
        this.rest = RestClient.builder().baseUrl(this.base).build();
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.timeout = timeout;
    }

    @Override
    public void indexar(Path documento) {
        String nome = documento.getFileName().toString();
        List<Documento> existentes = rest.get().uri("/api/documentos").retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        for (Documento d : existentes == null ? List.<Documento>of() : existentes) {
            if (d.nome().equals(nome)) {
                rest.delete().uri("/api/documentos/{id}", d.id()).retrieve().toBodilessEntity();
            }
        }
        var partes = new LinkedMultiValueMap<String, Object>();
        partes.add("arquivo", new FileSystemResource(documento));
        rest.post().uri("/api/documentos").contentType(MediaType.MULTIPART_FORM_DATA).body(partes)
                .retrieve().toBodilessEntity();
    }

    @Override
    public RespostaRag perguntar(String pergunta, String conversaId) {
        var corpo = JSON.writeValueAsString(Map.of("pergunta", pergunta, "conversaId", conversaId));
        var req = HttpRequest.newBuilder(URI.create(base + "/api/chat"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(corpo, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<java.util.stream.Stream<String>> r = http.send(req,
                    HttpResponse.BodyHandlers.ofLines());
            if (r.statusCode() != 200) {
                throw new IllegalStateException("O sistema avaliado respondeu HTTP " + r.statusCode());
            }
            return interpretar(r.body().toList());
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao consultar o sistema avaliado: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Consulta interrompida.", e);
        }
    }

    /** Junta os eventos SSE (fontes, token..., fim ou erro) numa resposta. */
    static RespostaRag interpretar(List<String> linhas) {
        String evento = null;
        var texto = new StringBuilder();
        List<RespostaRag.Fonte> fontes = new ArrayList<>();
        boolean erro = false;
        for (String linha : linhas) {
            if (linha.startsWith("event:")) {
                evento = linha.substring(6).strip();
            } else if (linha.startsWith("data:") && evento != null) {
                JsonNode d = JSON.readTree(linha.substring(5));
                switch (evento) {
                    case "fontes" -> d.forEach(f -> fontes.add(new RespostaRag.Fonte(
                            f.get("indice").asInt(),
                            f.get("documentoNome").asString(),
                            f.hasNonNull("pagina") ? f.get("pagina").asInt() : null,
                            f.get("texto").asString(),
                            f.get("similaridade").asDouble())));
                    case "token" -> texto.append(d.get("t").asString());
                    case "erro" -> {
                        erro = true;
                        texto.append(d.get("mensagem").asString());
                    }
                    default -> {
                    }
                }
            }
        }
        return new RespostaRag(texto.toString().strip(), fontes, erro);
    }
}
