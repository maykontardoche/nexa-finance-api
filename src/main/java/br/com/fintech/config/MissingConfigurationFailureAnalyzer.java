package br.com.fintech.config;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/** Troca o stack trace do startup por uma mensagem que nomeia a variável e aponta o exemplo. */
class MissingConfigurationFailureAnalyzer extends AbstractFailureAnalyzer<MissingConfigurationException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, MissingConfigurationException cause) {
        String variables = String.join(", ", cause.getVariables());
        return new FailureAnalysis(
                "Variável de ambiente obrigatória ausente ou vazia: " + variables + ".",
                "Defina " + variables + " no ambiente ou no arquivo .env na raiz do projeto. "
                        + "Copie .env.example para .env e ajuste os valores.",
                cause);
    }
}
