package com.alvaro.baixashopee.data.model;

import java.text.Normalizer;
import java.util.Locale;

public final class NeighborhoodHelper {
    public static final String GROUP_CABUCU = "Cabuçu";
    public static final String GROUP_BOM_JESUS = "Bom Jesus";
    public static final String GROUP_PRAIA_DO_SOL = "Praia do Sol";

    private NeighborhoodHelper() {}

    /**
     * Normaliza a chave do bairro para agrupamento e filtros.
     * trim, lowercase, remove acentos, ignora pontuação e espaços múltiplos.
     */
    public static String normalizeKey(String rawNeighborhood) {
        if (rawNeighborhood == null) return "";
        String clean = Normalizer.normalize(rawNeighborhood, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
        return clean;
    }

    /**
     * Retorna o nome canônico do grupo de bairro para exibição/ordenação.
     */
    public static String getCanonicalGroup(String rawNeighborhood) {
        if (rawNeighborhood == null || rawNeighborhood.trim().isEmpty()) {
            return "Sem bairro";
        }
        String key = normalizeKey(rawNeighborhood);
        if (key.isEmpty()) {
            return "Sem bairro";
        }

        // Regra especial Cabuçu: qualquer variante contendo "cabucu"
        if (key.contains("cabucu")) {
            return GROUP_CABUCU;
        }

        // Regra especial Bom Jesus: "bom jesus" ou "bom jesus dos pobres"
        if (key.contains("bom jesus")) {
            return GROUP_BOM_JESUS;
        }

        // Regra Praia do Sol
        if (key.contains("praia do sol")) {
            return GROUP_PRAIA_DO_SOL;
        }

        // Outros bairros: preserva a capitalização razoável original ou limpa
        String trimmed = rawNeighborhood.trim();
        return trimmed;
    }

    /**
     * Verifica se dois bairros pertencem à mesma categoria agrupada.
     */
    public static boolean belongToSameGroup(String neighborhood1, String neighborhood2) {
        String group1 = getCanonicalGroup(neighborhood1);
        String group2 = getCanonicalGroup(neighborhood2);
        return group1.equalsIgnoreCase(group2);
    }
}
