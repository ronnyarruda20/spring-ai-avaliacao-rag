package io.github.ronnyarruda20.avaliacao.alvo;

import java.nio.file.Path;

/** O sistema avaliado, visto de fora. */
public interface SistemaRag {

    /** Garante que o documento está indexado, substituindo uma versão anterior com o mesmo nome. */
    void indexar(Path documento);

    RespostaRag perguntar(String pergunta, String conversaId);
}
