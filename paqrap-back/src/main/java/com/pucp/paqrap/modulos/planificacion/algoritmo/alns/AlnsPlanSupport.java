package com.pucp.paqrap.modulos.planificacion.algoritmo.alns;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;

/**
 * Utilidades compartidas por los operadores ALNS.
 */
final class AlnsPlanSupport {

    private AlnsPlanSupport() {
    }

    /**
     * Devuelve los pedidos contenidos actualmente
     * en el plan sin duplicar ids.
     */
    static List<Order> ordersInPlan(
            OperationalPlan plan
    ) {

        LinkedHashMap<String, Order> orders =
                new LinkedHashMap<>();

        for (
                DeliveryRoute route :
                plan.routes()
        ) {

            for (
                    var stop :
                    route.stops()
            ) {

                if (
                        stop instanceof DeliveryStop delivery
                ) {

                    orders.putIfAbsent(
                            delivery.order().id(),
                            delivery.order()
                    );
                }
            }
        }

        return List.copyOf(
                orders.values()
        );
    }

    /**
     * Primera versión conservadora del destroy.
     *
     * Una ruta solo se puede retirar si ninguno de
     * sus pedidos está distribuido entre vehículos
     * diferentes.
     *
     * Esto evita dejar un pedido parcialmente atendido
     * durante el proceso destroy -> repair.
     */
    static List<DeliveryRoute> safelyRemovableRoutes(
            OperationalPlan plan
    ) {

        Map<String, Set<String>> vehiclesByOrder =
                new HashMap<>();

        for (
                DeliveryRoute route :
                plan.routes()
        ) {

            for (
                    var stop :
                    route.stops()
            ) {

                if (
                        stop instanceof DeliveryStop delivery
                ) {

                    vehiclesByOrder
                            .computeIfAbsent(
                                    delivery.order().id(),
                                    ignored ->
                                            new HashSet<>()
                            )
                            .add(
                                    route
                                            .vehicle()
                                            .id()
                            );
                }
            }
        }

        List<DeliveryRoute> removable =
                new ArrayList<>();

        for (
                DeliveryRoute route :
                plan.routes()
        ) {

            boolean hasDelivery =
                    false;

            boolean safe =
                    true;

            for (
                    var stop :
                    route.stops()
            ) {

                if (
                        stop instanceof DeliveryStop delivery
                ) {

                    hasDelivery =
                            true;

                    Set<String> vehicles =
                            vehiclesByOrder
                                    .getOrDefault(
                                            delivery
                                                    .order()
                                                    .id(),
                                            Set.of()
                                    );

                    if (
                            vehicles.size() != 1 ||
                            !vehicles.contains(
                                    route
                                            .vehicle()
                                            .id()
                            )
                    ) {

                        safe =
                                false;

                        break;
                    }
                }
            }

            if (
                    hasDelivery &&
                    safe
            ) {

                removable.add(
                        route
                );
            }
        }

        return List.copyOf(
                removable
        );
    }

    /**
     * Convierte las entregas retiradas en demandas
     * que luego utilizarán los operadores repair.
     *
     * Si un pedido aparece varias veces dentro de la
     * misma ruta por fraccionamiento, se consolida.
     */
    static List<DeliveryDemand> demandsFromRoutes(
            Collection<DeliveryRoute> routes
    ) {

        record Acc(
                Order order,
                int packages
        ) {
        }

        LinkedHashMap<String, Acc> byOrder =
                new LinkedHashMap<>();

        for (
                DeliveryRoute route :
                routes
        ) {

            for (
                    var stop :
                    route.stops()
            ) {

                if (
                        stop instanceof DeliveryStop delivery
                ) {

                    Acc current =
                            byOrder.get(
                                    delivery
                                            .order()
                                            .id()
                            );

                    int quantity =
                            delivery
                                    .deliveredPackages() +
                            (
                                    current == null
                                            ? 0
                                            : current.packages()
                            );

                    byOrder.put(
                            delivery
                                    .order()
                                    .id(),
                            new Acc(
                                    delivery.order(),
                                    quantity
                            )
                    );
                }
            }
        }

        List<DeliveryDemand> result =
                new ArrayList<>(
                        byOrder.size()
                );

        for (
                Acc acc :
                byOrder.values()
        ) {

            result.add(
                    new DeliveryDemand(
                            acc.order(),
                            acc.packages()
                    )
            );
        }

        return List.copyOf(
                result
        );
    }
}