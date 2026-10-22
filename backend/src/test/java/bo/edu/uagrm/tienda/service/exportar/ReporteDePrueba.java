package bo.edu.uagrm.tienda.service.exportar;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import bo.edu.uagrm.tienda.dto.reportes.ClienteInactivo;
import bo.edu.uagrm.tienda.dto.reportes.ProductoPerdido;
import bo.edu.uagrm.tienda.dto.reportes.ProductoSinVenta;
import bo.edu.uagrm.tienda.dto.reportes.ProductoVendido;
import bo.edu.uagrm.tienda.dto.reportes.ResumenDePerdidos;
import bo.edu.uagrm.tienda.dto.reportes.VentasResponse;
import bo.edu.uagrm.tienda.dto.reportes.VentasResponse.PeriodoResponse;
import bo.edu.uagrm.tienda.dto.reportes.VentasResponse.PorCategoria;
import bo.edu.uagrm.tienda.dto.reportes.VentasResponse.PorDiaSemana;
import bo.edu.uagrm.tienda.dto.reportes.VentasResponse.PorFecha;
import bo.edu.uagrm.tienda.dto.reportes.VentasResponse.PorHora;
import bo.edu.uagrm.tienda.dto.reportes.VentasResponse.PorTipo;
import bo.edu.uagrm.tienda.dto.reportes.VentasResponse.Totales;
import bo.edu.uagrm.tienda.entity.TipoCliente;
import bo.edu.uagrm.tienda.entity.UnidadVenta;
import bo.edu.uagrm.tienda.service.ReporteCompleto;

// Reportes armados a mano para probar los archivos (HU-19) sin base de datos: octubre de 2025, comparado con
// septiembre, pedido por «Fabio Cruz» el 22/10/2025 a las 15:30
final class ReporteDePrueba {

	static final LocalDate DESDE = LocalDate.of(2025, 10, 1);
	static final LocalDate HASTA = LocalDate.of(2025, 10, 22);
	static final LocalDateTime GENERADO = LocalDateTime.of(2025, 10, 22, 15, 30);

	private ReporteDePrueba() {
	}

	static BigDecimal bs(String valor) {
		return new BigDecimal(valor);
	}

	// Con ventas: una llave sin costo, un taladro con margen y un producto que se vende a pérdida
	static ReporteCompleto conVentas() {
		return conMasVendidos(List.of(
				vendido(1L, "Taladro", "2", "900.00", "150.00"),
				vendido(2L, "Llave ñandú 🔧", "5", "50.00", null),
				vendido(3L, "=1+1", "1", "20.00", "-5.00")));
	}

	static ReporteCompleto conMasVendidos(List<ProductoVendido> masVendidos) {
		Totales actual = new Totales(bs("970.00"), 3, bs("323.33"), bs("145.00"), bs("15.6"), bs("50.00"), 1,
				bs("80.00"));
		Totales anterior = new Totales(bs("500.00"), 2, bs("250.00"), null, null, bs("500.00"), 0, bs("0.00"));
		List<PorFecha> porFecha = IntStream.rangeClosed(1, 22)
				.mapToObj(dia -> new PorFecha(DESDE.withDayOfMonth(dia).toString(),
						dia == 2 ? bs("970.00") : bs("0.00"), dia == 2 ? 3 : 0))
				.toList();
		VentasResponse ventas = new VentasResponse(
				new PeriodoResponse(DESDE, HASTA, LocalDate.of(2025, 9, 9), LocalDate.of(2025, 9, 30), "DIA"),
				actual, anterior, porFecha, porDiaSemana(bs("970.00"), 3), porHora(bs("970.00"), 3),
				List.of(new PorCategoria("Herramientas", bs("970.00"), 3, bs("145.00"))), porTipo(bs("970.00"), 3));
		return new ReporteCompleto(ventas, new ResumenDePerdidos(1, bs("120.00"), 4, 3, bs("75.0")), masVendidos,
				List.of(vendido(4L, "Sierra", "0", "0.00", null), vendido(2L, "Llave ñandú 🔧", "5", "50.00", null)),
				List.of(new ProductoSinVenta(5L, "HER-0005", "Rastrillo", UnidadVenta.UNIDAD, bs("3"), bs("20.00"),
						null, null, 100, bs("60.00"), true)),
				List.of(new ProductoPerdido(1L, "HER-0001", "Taladro", UnidadVenta.UNIDAD, bs("1"), bs("120.00"))),
				List.of(new ClienteInactivo(9L, "Ana", "Paz", "ana@mail.com", "70000000", TipoCliente.CONTRATISTA,
						LocalDateTime.of(2025, 7, 1, 10, 0), 4, bs("1200.00"))),
				TipoCliente.CONTRATISTA, 60, GENERADO, "Fabio Cruz");
	}

	// Sin ventas en el período: las listas que no dependen de él siguen con filas (RF-9)
	static ReporteCompleto sinVentas() {
		Totales cero = new Totales(bs("0.00"), 0, null, null, null, bs("0.00"), 0, bs("0.00"));
		List<PorFecha> porFecha = IntStream.rangeClosed(1, 22)
				.mapToObj(dia -> new PorFecha(DESDE.withDayOfMonth(dia).toString(), bs("0.00"), 0)).toList();
		VentasResponse ventas = new VentasResponse(
				new PeriodoResponse(DESDE, HASTA, LocalDate.of(2025, 9, 9), LocalDate.of(2025, 9, 30), "DIA"), cero,
				cero, porFecha, porDiaSemana(bs("0.00"), 0), porHora(bs("0.00"), 0), List.of(),
				porTipo(bs("0.00"), 0));
		return new ReporteCompleto(ventas, new ResumenDePerdidos(0, bs("0.00"), 0, 0, null), List.of(),
				List.of(vendido(4L, "Sierra", "0", "0.00", null)),
				List.of(new ProductoSinVenta(5L, "HER-0005", "Rastrillo", UnidadVenta.UNIDAD, bs("3"), bs("20.00"),
						null, null, 100, bs("60.00"), true)),
				List.of(), List.of(), null, 30, GENERADO, "Fabio Cruz");
	}

	// Un año: por mes, 13 filas (de octubre de 2024 a octubre de 2025)
	static ReporteCompleto unAno() {
		ReporteCompleto base = sinVentas();
		LocalDate desde = LocalDate.of(2024, 10, 22);
		List<PorFecha> meses = new ArrayList<>();
		for (int i = 0; i < 13; i++) {
			meses.add(new PorFecha(desde.plusMonths(i).toString().substring(0, 7), bs("10.00"), 1));
		}
		Totales actual = new Totales(bs("130.00"), 13, bs("10.00"), null, null, bs("130.00"), 0, bs("0.00"));
		VentasResponse ventas = new VentasResponse(
				new PeriodoResponse(desde, HASTA, LocalDate.of(2023, 10, 21), LocalDate.of(2024, 10, 21), "MES"),
				actual, base.ventas().anterior(), meses, porDiaSemana(bs("130.00"), 13), porHora(bs("130.00"), 13),
				List.of(), porTipo(bs("130.00"), 13));
		return new ReporteCompleto(ventas, base.perdidos(), base.masVendidos(), base.menosVendidos(),
				base.sinVenta(), base.productosPerdidos(), base.clientesInactivos(), null, 30, GENERADO,
				"Fabio Cruz");
	}

	static ProductoVendido vendido(Long id, String nombre, String cantidad, String importe, String margen) {
		return new ProductoVendido(id, "HER-%04d".formatted(id), nombre, UnidadVenta.UNIDAD, true, bs(cantidad),
				bs(importe), margen == null ? null : bs(margen), margen == null ? bs(importe) : bs("0.00"));
	}

	private static List<PorDiaSemana> porDiaSemana(BigDecimal jueves, long pedidos) {
		return IntStream.rangeClosed(1, 7)
				.mapToObj(dia -> new PorDiaSemana(dia, dia == 4 ? jueves : bs("0.00"), dia == 4 ? pedidos : 0))
				.toList();
	}

	private static List<PorHora> porHora(BigDecimal diez, long pedidos) {
		return IntStream.range(0, 24)
				.mapToObj(hora -> new PorHora(hora, hora == 10 ? diez : bs("0.00"), hora == 10 ? pedidos : 0))
				.toList();
	}

	private static List<PorTipo> porTipo(BigDecimal contratista, long pedidos) {
		return List.of(TipoCliente.values()).stream()
				.map(tipo -> tipo == TipoCliente.CONTRATISTA
						? new PorTipo(tipo, contratista, pedidos,
								pedidos == 0 ? null : contratista.divide(BigDecimal.valueOf(pedidos), 2,
										java.math.RoundingMode.HALF_UP))
						: new PorTipo(tipo, bs("0.00"), 0, null))
				.toList();
	}
}
