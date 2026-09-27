package com.alvaro.baixashopee.data.model;

import com.alvaro.baixashopee.Delivery;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

public final class QueueOrganizer {
    private QueueOrganizer() {}

    /**
     * Agrupa uma lista plana de pacotes em DeliveryGroups consecutivos por destinatário.
     * Obedece estritamente à identidade do destinatário.
     */
    public static List<DeliveryGroup> groupDeliveries(List<Delivery> deliveries) {
        if (deliveries == null || deliveries.isEmpty()) {
            return new ArrayList<>();
        }

        LinkedHashMap<String, DeliveryGroup> map = new LinkedHashMap<>();
        for (Delivery delivery : deliveries) {
            if (delivery == null || delivery.trackingCode == null || delivery.trackingCode.isEmpty()) {
                continue;
            }
            String key = RecipientNameHelper.getRecipientKey(delivery.customerName, delivery.trackingCode);
            DeliveryGroup group = map.get(key);
            if (group == null) {
                group = new DeliveryGroup(key, delivery.customerName);
                map.put(key, group);
            }
            group.addPackage(delivery);
        }

        return new ArrayList<>(map.values());
    }

    /**
     * Ordena os grupos aplicando as regras do projeto:
     * 1. Destinatários normais primeiro, destinatários com nome TOTALMENTE EM MAIÚSCULAS no bloco final.
     * 2. Dentro do grupo normal e maiúsculo, aplica a ordenação selecionada (MANUAL, NAME_AZ, NEIGHBORHOOD).
     */
    public static List<DeliveryGroup> sortGroups(List<DeliveryGroup> groups, SortOrder sortOrder) {
        if (groups == null || groups.isEmpty()) {
            return new ArrayList<>();
        }

        List<DeliveryGroup> normalList = new ArrayList<>();
        List<DeliveryGroup> uppercaseList = new ArrayList<>();

        for (DeliveryGroup group : groups) {
            if (group.isAllUppercase()) {
                uppercaseList.add(group);
            } else {
                normalList.add(group);
            }
        }

        final Collator collator = Collator.getInstance(new Locale("pt", "BR"));
        collator.setStrength(Collator.TERTIARY);

        Comparator<DeliveryGroup> comparator;
        if (sortOrder == SortOrder.NAME_AZ) {
            comparator = (g1, g2) -> collator.compare(g1.getRecipientName(), g2.getRecipientName());
            normalList.sort(comparator);
            uppercaseList.sort(comparator);
        } else if (sortOrder == SortOrder.NEIGHBORHOOD) {
            comparator = (g1, g2) -> {
                String n1 = NeighborhoodHelper.getCanonicalGroup(g1.getNeighborhood());
                String n2 = NeighborhoodHelper.getCanonicalGroup(g2.getNeighborhood());
                int cmp = collator.compare(n1, n2);
                if (cmp != 0) return cmp;
                return collator.compare(g1.getRecipientName(), g2.getRecipientName());
            };
            normalList.sort(comparator);
            uppercaseList.sort(comparator);
        }
        // No caso de MANUAL, a ordem atual é preservada em normalList e uppercaseList!

        List<DeliveryGroup> result = new ArrayList<>(normalList.size() + uppercaseList.size());
        result.addAll(normalList);
        result.addAll(uppercaseList);
        return result;
    }

    /**
     * Filtra grupos por bairro selecionado (se neighborhoodFilter for não nulo e não "TODOS").
     */
    public static List<DeliveryGroup> filterByNeighborhood(List<DeliveryGroup> groups, String selectedNeighborhood) {
        if (groups == null) return new ArrayList<>();
        if (selectedNeighborhood == null || selectedNeighborhood.trim().isEmpty()
                || selectedNeighborhood.equalsIgnoreCase("TODOS")
                || selectedNeighborhood.equalsIgnoreCase("TODOS OS BAIRROS")) {
            return groups;
        }

        List<DeliveryGroup> filtered = new ArrayList<>();
        for (DeliveryGroup group : groups) {
            if (NeighborhoodHelper.belongToSameGroup(group.getNeighborhood(), selectedNeighborhood)) {
                filtered.add(group);
            }
        }
        return filtered;
    }

    /**
     * Converte os grupos de volta em uma lista plana de Deliveries.
     * Garante que pacotes da mesma pessoa fiquem sempre consecutivos.
     */
    public static List<Delivery> flatten(List<DeliveryGroup> groups) {
        List<Delivery> result = new ArrayList<>();
        if (groups == null) return result;
        for (DeliveryGroup group : groups) {
            result.addAll(group.getPackages());
        }
        return result;
    }

    /**
     * Pipeline completo: agrupa -> filtra -> ordena -> achata em lista plana.
     */
    public static List<Delivery> organize(List<Delivery> deliveries, SortOrder sortOrder, String neighborhoodFilter) {
        List<DeliveryGroup> groups = groupDeliveries(deliveries);
        if (neighborhoodFilter != null && !neighborhoodFilter.isEmpty()) {
            groups = filterByNeighborhood(groups, neighborhoodFilter);
        }
        groups = sortGroups(groups, sortOrder);
        return flatten(groups);
    }
}
