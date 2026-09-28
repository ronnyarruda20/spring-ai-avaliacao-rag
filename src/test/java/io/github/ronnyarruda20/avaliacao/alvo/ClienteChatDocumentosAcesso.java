package io.github.ronnyarruda20.avaliacao.alvo;

import java.util.List;

/** Expõe aos testes de outro pacote o método de pacote que interpreta o SSE. */
public final class ClienteChatDocumentosAcesso {

    private ClienteChatDocumentosAcesso() {
    }

    public static RespostaRag interpretar(List<String> linhas) {
        return ClienteChatDocumentos.interpretar(linhas);
    }
}
