package io.github.ronnyarruda20.avaliacao.juiz;

import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;

import io.github.ronnyarruda20.avaliacao.alvo.RespostaRag;
import io.github.ronnyarruda20.avaliacao.conjunto.Conjunto.Caso;

/**
 * Modelo avaliador ("LLM como juiz"). Usa um modelo diferente e mais forte que o avaliado,
 * com uma rubrica fechada e saída estruturada, para que a nota não dependa de interpretar texto livre.
 */
public class JuizLlm implements Juiz {

    static final String RUBRICA = """
            Você avalia respostas de um sistema que responde perguntas sobre documentos de uma empresa.
            Julgue apenas o que está escrito; não use conhecimento próprio sobre o assunto.

            1. Fatos esperados: para cada fato, "presente" é verdadeiro se a resposta afirma a mesma \
            informação, mesmo com outras palavras ou outro formato de número. É falso se a resposta \
            omite o fato, afirma algo diferente ou se contradiz.
            2. Fidelidade: liste as afirmações factuais da resposta que NÃO estão sustentadas pelos \
            trechos recuperados. A nota vai de 0 a 1 e é a fração das afirmações factuais sustentadas. \
            Frases de cortesia, citações como [1] e perguntas ao usuário não contam como afirmações. \
            Uma resposta que só diz que não encontrou a informação tem fidelidade 1.
            3. Recusa: "recusou" é verdadeiro se a resposta diz que não encontrou a informação nos \
            documentos em vez de responder.
            Explique o julgamento em uma frase no comentário.
            """;

    private final ChatClient chat;

    public JuizLlm(ChatClient.Builder builder) {
        this.chat = builder.defaultSystem(RUBRICA).build();
    }

    @Override
    public Veredito avaliar(Caso caso, RespostaRag resposta) {
        return chat.prompt().user(entrada(caso, resposta)).call().entity(Veredito.class);
    }

    static String entrada(Caso caso, RespostaRag resposta) {
        String trechos = resposta.fontes().isEmpty() ? "(nenhum trecho recuperado)"
                : resposta.fontes().stream()
                        .map(f -> "[" + f.indice() + "] " + f.documentoNome()
                                + (f.pagina() == null ? "" : ", página " + f.pagina()) + "\n" + f.texto().strip())
                        .collect(Collectors.joining("\n\n"));
        String fatos = caso.fatos().isEmpty() ? "(nenhum: a informação não está nos documentos)"
                : caso.fatos().stream().map(f -> "- " + f).collect(Collectors.joining("\n"));
        return """
                PERGUNTA
                %s

                RESPOSTA DO SISTEMA
                %s

                TRECHOS RECUPERADOS
                %s

                FATOS ESPERADOS
                %s
                """.formatted(caso.pergunta(), resposta.texto(), trechos, fatos);
    }
}
