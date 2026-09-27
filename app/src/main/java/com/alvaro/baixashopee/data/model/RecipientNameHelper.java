package com.alvaro.baixashopee.data.model;

public final class RecipientNameHelper {
    private RecipientNameHelper() {}

    /**
     * Verifica se o nome do destinatário é composto inteiramente por letras MAIÚSCULAS.
     * Caracteres não alfabéticos (números, espaços, pontuação, hífens) são ignorados na verificação.
     * Retorna true apenas se houver pelo menos uma letra e todas as letras forem maiúsculas.
     */
    public static boolean isAllUppercase(String name) {
        if (name == null) return false;
        String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.equals("-")) return false;

        boolean hasLetter = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (Character.isLetter(c)) {
                hasLetter = true;
                if (!Character.isUpperCase(c)) {
                    return false;
                }
            }
        }
        return hasLetter;
    }

    /**
     * Compara dois nomes de forma estrita e literal (case-sensitive e com acentos).
     * Sequence vazio ou "-" nunca é considerado igual a outro sequence vazio/"-".
     */
    public static boolean areSameRecipient(String name1, String name2) {
        if (name1 == null || name2 == null) return false;
        String t1 = name1.trim();
        String t2 = name2.trim();
        if (t1.isEmpty() || t2.isEmpty() || t1.equals("-") || t2.equals("-")) {
            return false;
        }
        return t1.equals(t2);
    }

    /**
     * Gera uma chave estável de destinatário para agrupamento de pacotes na fila.
     * Se for vazio ou "-", usa um fallback único (como o próprio trackingCode) para não misturar pessoas.
     */
    public static String getRecipientKey(String customerName, String fallbackTrackingCode) {
        if (customerName == null) return "pkg_" + fallbackTrackingCode;
        String trimmed = customerName.trim();
        if (trimmed.isEmpty() || trimmed.equals("-")) {
            return "pkg_" + fallbackTrackingCode;
        }
        return "name_" + trimmed;
    }
}
