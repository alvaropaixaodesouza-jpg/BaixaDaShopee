package com.alvaro.baixashopee;

import com.alvaro.baixashopee.data.model.DeliveryGroup;
import com.alvaro.baixashopee.data.model.QueueOrganizer;
import com.alvaro.baixashopee.photo.TempMediaStoreManager;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class Phase7To11BusinessRulesTest {

    @Test
    public void testKeyboardHeightClamping() {
        int belowMin = 180;
        int clampedMin = Math.max(DeliveryKeyboardService.MIN_HEIGHT_DP, Math.min(belowMin, DeliveryKeyboardService.MAX_HEIGHT_DP));
        assertEquals(DeliveryKeyboardService.MIN_HEIGHT_DP, clampedMin);

        int aboveMax = 500;
        int clampedMax = Math.max(DeliveryKeyboardService.MIN_HEIGHT_DP, Math.min(aboveMax, DeliveryKeyboardService.MAX_HEIGHT_DP));
        assertEquals(DeliveryKeyboardService.MAX_HEIGHT_DP, clampedMax);

        int normal = 300;
        int clampedNormal = Math.max(DeliveryKeyboardService.MIN_HEIGHT_DP, Math.min(normal, DeliveryKeyboardService.MAX_HEIGHT_DP));
        assertEquals(300, clampedNormal);
    }

    @Test
    public void testTempMediaStoreTTLConstant() {
        assertEquals("TTL de fotos temporárias deve ser 5 minutos (300.000 ms)",
                300_000L, TempMediaStoreManager.TTL_MS);
    }

    @Test
    public void testMultiplosPacotesMesmoDestinatarioPermanecemConsecutivosAposModificacao() {
        Delivery p1 = new Delivery("BR001", "Alvaro Paixao", "Rua Bahia, 10");
        Delivery p2 = new Delivery("BR002", "Alvaro Paixao", "Rua Bahia, 10");
        Delivery p3 = new Delivery("BR003", "Carlos Souza", "Rua Bahia, 20");
        Delivery p4 = new Delivery("BR004", "Alvaro Paixao", "Rua Bahia, 10");

        List<DeliveryGroup> groups = QueueOrganizer.groupDeliveries(Arrays.asList(p1, p2, p3, p4));
        List<Delivery> organized = QueueOrganizer.flatten(groups);

        assertEquals("Alvaro deve ter 3 pacotes juntos", "Alvaro Paixao", organized.get(0).customerName);
        assertEquals("Alvaro deve ter 3 pacotes juntos", "Alvaro Paixao", organized.get(1).customerName);
        assertEquals("Alvaro deve ter 3 pacotes juntos", "Alvaro Paixao", organized.get(2).customerName);
        assertEquals("Carlos deve ser o 4º pacote", "Carlos Souza", organized.get(3).customerName);
    }

    @Test
    public void testReinsercaoNoBlocoCorretoAoRestaurar() {
        Delivery d1 = new Delivery("BR1", "Maria", "Rua 1");
        Delivery d2 = new Delivery("BR2", "Maria", "Rua 1");
        Delivery d3 = new Delivery("BR3", "Pedro", "Rua 2");

        List<Delivery> activeQueue = new ArrayList<>(Arrays.asList(d1, d2, d3));
        Delivery restored = new Delivery("BR4", "Maria", "Rua 1");

        // Simula busca pelo bloco da Maria
        int insertIndex = -1;
        for (int i = 0; i < activeQueue.size(); i++) {
            if (activeQueue.get(i).customerName.equals(restored.customerName)) {
                insertIndex = i + 1;
            }
        }
        activeQueue.add(insertIndex, restored);

        assertEquals("Maria", activeQueue.get(0).customerName);
        assertEquals("Maria", activeQueue.get(1).customerName);
        assertEquals("Maria", activeQueue.get(2).customerName);
        assertEquals("BR4", activeQueue.get(2).trackingCode);
        assertEquals("Pedro", activeQueue.get(3).customerName);
    }
}
