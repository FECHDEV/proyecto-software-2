package bo.edu.uagrm.tienda.dto.reportes;

import java.math.BigDecimal;

import bo.edu.uagrm.tienda.entity.EstadoPedido;

// Cuántos pedidos confirmados en el período quedaron en cada estado, y su importe (HU-18 RF-17)
public record EstadoFila(EstadoPedido estado, Long pedidos, BigDecimal importe) {
}
