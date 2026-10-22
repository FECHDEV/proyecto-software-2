package bo.edu.uagrm.tienda.dto.reportes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import bo.edu.uagrm.tienda.entity.TipoCliente;

// GET /api/reportes/ventas (HU-18): el resumen del período, sin las listas ni los perdidos (que tienen lo suyo). Un
// promedio o un porcentaje sin con qué dividir es nulo, igual que el margen si nada vendido tenía costo
public record VentasResponse(PeriodoResponse periodo, Totales actual, Totales anterior, List<PorFecha> porFecha,
		List<PorDiaSemana> porDiaSemana, List<PorHora> porHora, List<PorCategoria> porCategoria,
		List<PorTipo> porTipo) {

	// agrupado: DIA o MES (RF-9)
	public record PeriodoResponse(LocalDate desde, LocalDate hasta, LocalDate anteriorDesde, LocalDate anteriorHasta,
			String agrupado) {
	}

	// RF-6 a RF-8
	public record Totales(BigDecimal ingresos, long pedidos, BigDecimal promedio, BigDecimal margen,
			BigDecimal margenPorcentaje, BigDecimal vendidoSinCosto, long devolverCantidad,
			BigDecimal devolverImporte) {
	}

	// fecha: AAAA-MM-DD, o AAAA-MM si va por mes (RF-9)
	public record PorFecha(String fecha, BigDecimal ingresos, long pedidos) {
	}

	// dia: 1 lunes … 7 domingo (RF-10)
	public record PorDiaSemana(int dia, BigDecimal ingresos, long pedidos) {
	}

	// RF-10
	public record PorHora(int hora, BigDecimal ingresos, long pedidos) {
	}

	// RF-14
	public record PorCategoria(String categoria, BigDecimal ingresos, long pedidos, BigDecimal margen) {
	}

	// RF-15
	public record PorTipo(TipoCliente tipo, BigDecimal ingresos, long pedidos, BigDecimal promedio) {
	}
}
