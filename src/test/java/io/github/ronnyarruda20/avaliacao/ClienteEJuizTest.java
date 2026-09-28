package io.github.ronnyarruda20.avaliacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import io.github.ronnyarruda20.avaliacao.alvo.ClienteChatDocumentosAcesso;
import io.github.ronnyarruda20.avaliacao.alvo.RespostaRag;
import io.github.ronnyarruda20.avaliacao.alvo.RespostaRag.Fonte;
import io.github.ronnyarruda20.avaliacao.alvo.SistemaSabotadoAcesso;
import io.github.ronnyarruda20.avaliacao.juiz.JuizLlm;

class ClienteEJuizTest {

    @Test
    void juntaOsEventosSseNumaResposta() {
        var r = ClienteChatDocumentosAcesso.interpretar(List.of(
                "event:fontes",
                "data:[{\"indice\":1,\"documentoId\":\"d\",\"documentoNome\":\"regulamento.pdf\",\"pagina\":2,\"trecho\":1,\"texto\":\"R$ 150,00\",\"similaridade\":0.8}]",
                "",
                "event:token", "data:{\"t\":\"É R$ \"}", "",
                "event:token", "data:{\"t\":\"150,00 [1].\"}", "",
                "event:fim", "data:{\"conversaId\":\"c\"}", ""));

        assertThat(r.texto()).isEqualTo("É R$ 150,00 [1].");
        assertThat(r.fontes()).singleElement().satisfies(f -> {
            assertThat(f.documentoNome()).isEqualTo("regulamento.pdf");
            assertThat(f.pagina()).isEqualTo(2);
        });
        assertThat(r.erro()).isFalse();
    }

    @Test
    void eventoDeErroMarcaARespostaComoFalha() {
        var r = ClienteChatDocumentosAcesso.interpretar(List.of("event:erro", "data:{\"mensagem\":\"fora do ar\"}"));

        assertThat(r.erro()).isTrue();
        assertThat(r.texto()).isEqualTo("fora do ar");
    }

    @Test
    void sabotagensSeAlternamECadaUmaEstragaUmaCoisa() {
        var boa = new RespostaRag("A ajuda é de R$ 150,00 [1].", List.of(new Fonte(1, "a.pdf", 2, "x", 0.8)), false);

        assertThat(SistemaSabotadoAcesso.sabotar(boa, 0).texto()).isEqualTo("A ajuda é de R$ 187,00 [1].");
        assertThat(SistemaSabotadoAcesso.sabotar(boa, 1).texto()).isEqualTo("A ajuda é de R$ 150,00.");
        assertThat(SistemaSabotadoAcesso.sabotar(boa, 2).texto()).contains("bônus anual");
        assertThat(SistemaSabotadoAcesso.sabotar(boa, 3).fontes()).isEmpty();
        assertThat(SistemaSabotadoAcesso.sabotar(new RespostaRag("Não encontrei isso.", List.of(), false), 0).texto())
                .contains("R$ 35,00");
    }

    @Test
    void juizLeOVereditoEstruturadoDoModelo() {
        String json = """
                {"fatos":[{"fato":"R$ 150,00","presente":true,"justificativa":"Afirma o valor."}],
                 "fidelidade":1.0,"afirmacoesSemSuporte":[],"recusou":false,"comentario":"Correta."}""";
        ChatModel modelo = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                String sistema = prompt.getInstructions().getFirst().getText();
                assertThat(sistema).contains("Fatos esperados").contains("Fidelidade").contains("Recusa");
                return new ChatResponse(List.of(new Generation(new AssistantMessage(json))));
            }
        };
        var juiz = new JuizLlm(ChatClient.builder(modelo));

        var v = juiz.avaliar(ConjuntoEMetricasTest.CASO,
                new RespostaRag("É R$ 150,00 [1].", List.of(new Fonte(1, "regulamento.pdf", 2, "R$ 150,00", 0.8)), false));

        assertThat(v.fatos()).singleElement().satisfies(f -> assertThat(f.presente()).isTrue());
        assertThat(v.fidelidade()).isEqualTo(1.0);
        assertThat(v.recusou()).isFalse();
    }
}
