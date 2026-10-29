import type {
  EstadoVehiculoBackend,
  TipoVehiculoBackend,
} from "@/api/flota.dto";
import type { TipoAlmacenBackend } from "@/api/almacenes.dto";
import type { MotivoDetencionBackend } from "@/api/monitoreo.dto";

export interface TurnoDetalleDto {
  inicio: string;
  fin: string;
}

export interface ProximaEntregaDto {
  orden: number;
  pedidoId: string;
  x: number;
  y: number;
  paquetes: number;
  llegadaPrevista: string | null;
}

export interface VehiculoDetalleResponseDto {
  id: string;
  tipo: TipoVehiculoBackend;
  estado: EstadoVehiculoBackend;
  x: number;
  y: number;
  cargaActual: number;
  capacidad: number;
  disponibleDesde: string | null;
  motivoDetencion: MotivoDetencionBackend | null;
  detenidoHasta: string | null;
  instante: string;
  turnoVigente: TurnoDetalleDto | null;
  ventanaAlimentacion: TurnoDetalleDto | null;
  rutaId: string | null;
  proximasEntregas: ProximaEntregaDto[];
}

export type NivelAlertaInventarioDto = "VERDE" | "AMBAR" | "ROJO";

export interface PuntoStockDto {
  instante: string;
  stock: number;
}

export interface MovimientoInventarioDto {
  id: number;
  instante: string;
  tipo: string;
  cantidad: number;
  stockAntes: number | null;
  stockDespues: number | null;
  pedidoId: string | null;
}

export interface AlmacenDetalleResponseDto {
  id: string;
  tipo: TipoAlmacenBackend;
  x: number;
  y: number;
  instante: string;
  stock: number | null;
  capacidad: number | null;
  ocupacionPct: number | null;
  ventanaHoras: number;
  consumoVentana: number;
  ultimaRecarga: string | null;
  proximaRecarga: string | null;
  historial: PuntoStockDto[];

  /** Campos agregados por la auditoría de inventario; opcionales para compatibilidad. */
  alertaBajoStock?: boolean;
  nivelAlerta?: NivelAlertaInventarioDto | null;
  movimientos?: MovimientoInventarioDto[];
}
