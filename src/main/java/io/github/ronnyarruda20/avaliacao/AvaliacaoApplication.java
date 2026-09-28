package io.github.ronnyarruda20.avaliacao;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import io.github.ronnyarruda20.avaliacao.alvo.ClienteChatDocumentos;
import io.github.ronnyarruda20.avaliacao.alvo.SistemaRag;
import io.github.ronnyarruda20.avaliacao.alvo.SistemaSabotado;
import io.github.ronnyarruda20.avaliacao.conjunto.Conjunto;
import io.github.ronnyarruda20.avaliacao.juiz.JuizLlm;

/**
 * Linha de comando. Sai com código 1 se alguma métrica ficar abaixo do limite, para o CI falhar.
 */
@SpringBootApplication
public class AvaliacaoApplication {

    private static final Logger log = LoggerFactory.getLogger(AvaliacaoApplication.class);

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(AvaliacaoApplication.class, args)));
    }

    static class Execucao implements ApplicationRunner, ExitCodeGenerator {

        private final Avaliador avaliador;
        private final Path conjunto;
        private final Path saida;
        private final String alvo;
        private final String juiz;
        private int codigo;

        Execucao(Avaliador avaliador, Path conjunto, Path saida, String alvo, String juiz) {
            this.avaliador = avaliador;
            this.conjunto = conjunto;
            this.saida = saida;
            this.alvo = alvo;
            this.juiz = juiz;
        }

        @Override
        public void run(ApplicationArguments args) {
            Conjunto c = Conjunto.ler(conjunto);
            log.info("Avaliando {} ({} casos) em {}, com {} como avaliador", c.nome(), c.casos().size(), alvo, juiz);
            var resultado = avaliador.avaliar(c, conjunto.getParent().resolve("documentos"),
                    r -> log.info("{} {} ({} ms){}", r.aprovado() ? "✔" : "✘", r.id(), r.duracaoMs(),
                            r.erro() == null ? "" : ": " + r.erro()));
            var arquivos = Relatorio.gravar(resultado, alvo, juiz, saida, LocalDateTime.now());
            log.info("\n{}\n\nRelatório: {}", Relatorio.resumo(resultado), arquivos.markdown());
            codigo = resultado.aprovado() ? 0 : 1;
            if (codigo != 0) {
                log.warn("Avaliação REPROVADA: veja o relatório.");
            }
        }

        @Override
        public int getExitCode() {
            return codigo;
        }
    }

    @Bean
    @Profile("!test")
    Execucao execucao(ChatClient.Builder builder, Environment env,
            @Value("${avaliacao.alvo}") String alvo,
            @Value("${avaliacao.conjunto}") Path conjunto,
            @Value("${avaliacao.saida}") Path saida,
            @Value("${avaliacao.timeout}") Duration timeout,
            @Value("${avaliacao.sabotar:false}") boolean sabotar) {
        String provedor = env.getProperty("spring.ai.model.chat", "google-genai");
        String modelo = "anthropic".equals(provedor)
                ? env.getProperty("spring.ai.anthropic.chat.options.model")
                : env.getProperty("spring.ai.google.genai.chat.options.model");
        SistemaRag sistema = new ClienteChatDocumentos(alvo, timeout);
        if (sabotar) {
            // Calibração: com respostas estragadas de propósito, a avaliação precisa reprovar.
            sistema = new SistemaSabotado(sistema);
        }
        var avaliador = new Avaliador(sistema, new JuizLlm(builder));
        return new Execucao(avaliador, conjunto, saida, sabotar ? alvo + " (SABOTADO para calibração)" : alvo,
                provedor + " / " + modelo);
    }
}
