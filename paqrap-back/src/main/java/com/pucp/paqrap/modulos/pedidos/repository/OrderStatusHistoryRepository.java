package com.pucp.paqrap.modulos.pedidos.repository;

import com.pucp.paqrap.modulos.pedidos.persistence.OrderStatusHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistoryEntity, Long> {

    List<OrderStatusHistoryEntity> findByOrderIdOrderByChangedAtAscStatusHistoryIdAsc(String orderId);

    /** Borra el historial de ejecución (todo menos REGISTERED) de los pedidos registrados desde {@code desde}. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from OrderStatusHistoryEntity h
            where h.status <> com.pucp.paqrap.modulos.pedidos.entity.OrderStatus.REGISTERED
              and h.orderId in (select o.orderId from OrderEntity o where o.registeredAt >= :desde)
            """)
    int borrarEjecucionDesde(@Param("desde") Instant desde);
}
