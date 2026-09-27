package com.alvaro.baixashopee.data.model;

import java.text.Normalizer;
import java.util.Locale;

public final class AddressNormalizer {
    private AddressNormalizer() {}

    /**
     * Normaliza o endereço para busca de casas existentes e prevenção de duplicatas.
     */
    public static String normalize(String address) {
        if (address == null) return "";
        return Normalizer.normalize(address.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    /**
     * Verifica se o endereço tem especificidade suficiente para representar uma casa permanente.
     * Endereços genéricos como "Cabuçu", "Bom Jesus", "Saubara", "Rua sem número" sem identificador
     * não devem agrupar todas as encomendas em uma única casa.
     */
    public static boolean isSpecificAddress(String address) {
        if (address == null) return false;
        String norm = normalize(address);
        if (norm.length() < 8) return false;

        // Proteção contra endereços genéricos que não especificam residência
        if (norm.equals("cabucu") || norm.equals("bom jesus") || norm.equals("bom jesus dos pobres")
                || norm.equals("saubara") || norm.equals("rua sem numero") || norm.equals("sem numero")
                || norm.equals("centro") || norm.equals("zona rural") || norm.equals("praia do sol")) {
            return false;
        }

        // Deve ter pelo menos um número ou complemento distintivo, ou extensão suficiente
        boolean hasDigit = norm.matches(".*\\d.*");
        boolean hasLength = norm.length() >= 15;
        return hasDigit || hasLength;
    }
}
