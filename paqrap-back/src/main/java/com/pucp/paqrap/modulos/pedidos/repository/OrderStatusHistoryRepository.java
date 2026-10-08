package com.pucp.paqrap.modulos.pedidos.repository;

import com.pucp.paqrap.modulos.pedidos.persistence.OrderStatusHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistoryEntity, Long> {

    List<OrderStatusHistoryEntity> findByOrderIdOrderByChangedAtAscStatusHistoryIdAsc(String orderId);
}
