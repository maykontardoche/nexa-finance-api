package br.com.fintech.config;

import java.util.List;

/** Variáveis obrigatórias ausentes ou vazias. Carrega só os nomes, nunca valores. */
public class MissingConfigurationException extends RuntimeException {

    private final List<String> variables;

    public MissingConfigurationException(List<String> variables) {
        super("Configuração obrigatória ausente: " + String.join(", ", variables));
        this.variables = List.copyOf(variables);
    }

    public List<String> getVariables() {
        return variables;
    }
}
