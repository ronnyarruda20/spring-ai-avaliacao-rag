package io.github.ronnyarruda20.avaliacao.alvo;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Calibração do avaliador: estraga de propósito as respostas de um sistema real.
 *
 * <p>Um avaliador que dá nota máxima para tudo não prova nada. Rodando com este invólucro,
 * as notas têm que cair; se não caírem, o avaliador está complacente. As sabotagens se
 * alternam entre as perguntas:
 * <ol>
 *   <li>troca o primeiro número da resposta (erro de correção);</li>
 *   <li>apaga as citações (erro de citação);</li>
 *   <li>acrescenta uma afirmação inventada (erro de fidelidade);</li>
 *   <li>descarta os trechos recuperados (erro de recuperação).</li>
 * </ol>
 * Perguntas que deveriam ser recusadas recebem uma resposta inventada.
 */
public class SistemaSabotado implements SistemaRag {

    static final String INVENCAO = " Além disso, todo colaborador recebe um bônus anual de R$ 2.000,00 [1].";
    static final String RESPOSTA_INVENTADA = "Segundo os documentos, o valor é de R$ 35,00 por mês [1].";
    private static final Pattern NUMERO = Pattern.compile("\\d+");

    private final SistemaRag real;
    private final AtomicInteger contador = new AtomicInteger();

    public SistemaSabotado(SistemaRag real) {
        this.real = real;
    }

    @Override
    public void indexar(Path documento) {
        real.indexar(documento);
    }

    @Override
    public RespostaRag perguntar(String pergunta, String conversaId) {
        RespostaRag r = real.perguntar(pergunta, conversaId);
        return sabotar(r, contador.getAndIncrement());
    }

    static RespostaRag sabotar(RespostaRag r, int vez) {
        if (r.erro()) {
            return r;
        }
        if (ehRecusa(r.texto())) {
            return new RespostaRag(RESPOSTA_INVENTADA, r.fontes(), false);
        }
        return switch (vez % 4) {
            case 0 -> new RespostaRag(trocarPrimeiroNumero(r.texto()), r.fontes(), false);
            case 1 -> new RespostaRag(r.texto().replaceAll("\\s*\\[\\d{1,3}]", ""), r.fontes(), false);
            case 2 -> new RespostaRag(r.texto() + INVENCAO, r.fontes(), false);
            default -> new RespostaRag(r.texto(), List.of(), false);
        };
    }

    static String trocarPrimeiroNumero(String texto) {
        Matcher m = NUMERO.matcher(texto);
        while (m.find()) {
            // Pula números de citação como [1]: trocar esses não muda o conteúdo.
            if (m.start() > 0 && texto.charAt(m.start() - 1) == '[') {
                continue;
            }
            int novo = Integer.parseInt(m.group()) + 37;
            return texto.substring(0, m.start()) + novo + texto.substring(m.end());
        }
        return texto + INVENCAO;
    }

    private static boolean ehRecusa(String texto) {
        String t = texto.toLowerCase();
        return t.contains("não encontrei") || t.contains("nao encontrei");
    }
}
