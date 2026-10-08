package com.pucp.paqrap.modulos.pedidos.repository;

import com.pucp.paqrap.modulos.pedidos.entity.DeliveryType;
import com.pucp.paqrap.modulos.pedidos.entity.OrderStatus;
import com.pucp.paqrap.modulos.pedidos.persistence.OrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OrderRepository extends JpaRepository<OrderEntity, String> {

    @Query("""
            select o from OrderEntity o
            where (:clienteId is null or o.clientId = :clienteId)
              and (:estado is null or o.status = :estado)
              and (:tipoEntrega is null or o.deliveryType = :tipoEntrega)
              and (:registradoDesde is null or o.registeredAt >= :registradoDesde)
              and (:registradoHasta is null or o.registeredAt < :registradoHasta)
              and (:venceHasta is null or o.deadline <= :venceHasta)
            """)
    Page<OrderEntity> buscar(@Param("clienteId") String clienteId,
                             @Param("estado") OrderStatus estado,
                             @Param("tipoEntrega") DeliveryType tipoEntrega,
                             @Param("registradoDesde") Instant registradoDesde,
                             @Param("registradoHasta") Instant registradoHasta,
                             @Param("venceHasta") Instant venceHasta,
                             Pageable pageable);

    /** Ids ya registrados de un archivo mensual (prefijo AAAAMM-). */
    @Query("select o.orderId from OrderEntity o where o.orderId like :prefijo")
    List<String> idsConPrefijo(@Param("prefijo") String prefijo);
}
