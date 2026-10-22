package bo.edu.uagrm.tienda.service.exportar;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import bo.edu.uagrm.tienda.dto.reportes.ClienteInactivo;
import bo.edu.uagrm.tienda.dto.reportes.ProductoPerdido;
import bo.edu.uagrm.tienda.dto.reportes.ProductoSinVenta;
import bo.edu.uagrm.tienda.dto.reportes.ProductoVendido;
import bo.edu.uagrm.tienda.dto.reportes.ResumenDePerdidos;
import bo.edu.uagrm.tienda.dto.reportes.VentasResponse;
import bo.edu.uagrm.tienda.dto.reportes.VentasResponse.PeriodoResponse;
import bo.edu.uagrm.tienda.dto.reportes.VentasResponse.Totales;
import bo.edu.uagrm.tienda.entity.TipoCliente;
import bo.edu.uagrm.tienda.entity.UnidadVenta;
import bo.edu.uagrm.tienda.service.ReporteCompleto;
import bo.edu.uagrm.tienda.service.exportar.Celda.Dinero;
import bo.edu.uagrm.tienda.service.exportar.Celda.Fecha;
import bo.edu.uagrm.tienda.service.exportar.Celda.FechaHora;
import bo.edu.uagrm.tienda.service.exportar.Celda.Numero;
import bo.edu.uagrm.tienda.service.exportar.Celda.Porcentaje;
import bo.edu.uagrm.tienda.service.exportar.Seccion.Hoja;

// El reporte exportado (HU-19) como hojas y secciones, una sola vez para el Excel y el PDF: los dos dicen lo mismo
// que la pantalla de HU-18, con sus textos (RF-5 a RF-9)
final class SeccionesDelReporte {

	static final String MARCA = "Tienda Montero — Reporte de ventas";
	static final String AVISO_DATOS_PERSONALES = "Contiene datos personales de clientes: uso interno.";

	private static final List<String> DIAS = List.of("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado",
			"Domingo");
	private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MMMM 'de' yyyy",
			Locale.forLanguageTag("es-BO"));
	private static final String SIN_COSTO = "sin costo";
	private static final String NADA = "—";

	private SeccionesDelReporte() {
	}

	// Con switch y no con un mapa: un valor nuevo del enum no compila hasta tener su nombre (/code-review)
	private static String tipo(TipoCliente tipo) {
		return switch (tipo) {
			case PUBLICO_GENERAL -> "Público general";
			case CONTRATISTA -> "Contratista";
			case TALLER -> "Taller";
			case EMPRESA -> "Empresa";
		};
	}

	private static String unidad(UnidadVenta unidad) {
		return switch (unidad) {
			case UNIDAD -> "Unidad";
			case METRO -> "Metro";
			case KILO -> "Kilo";
			case LITRO -> "Litro";
			case CAJA -> "Caja";
			case PAR -> "Par";
			case ROLLO -> "Rollo";
		};
	}

	static List<Hoja> de(ReporteCompleto reporte) {
		VentasResponse ventas = reporte.ventas();
		boolean sinVentas = ventas.actual().pedidos() == 0;
		String sinVentasTexto = reporte.tipo() == null ? "No hubo ventas en este período."
				: "No hubo ventas a clientes «%s» en este período.".formatted(tipo(reporte.tipo()));
		boolean porMes = "MES".equals(ventas.periodo().agrupado());
		List<Hoja> hojas = new ArrayList<>();
		hojas.add(new Hoja("Resumen", List.of(encabezado(reporte), cifras(ventas, sinVentas, sinVentasTexto))));
		hojas.add(hoja("Por fecha", new Seccion(porMes ? "Ventas por mes" : "Ventas por día", List.of(),
				List.of(porMes ? "Mes" : "Fecha", "Pedidos", "Ingresos"),
				sinVentas ? List.of() : ventas.porFecha().stream().map(fila -> List.of(
						porMes ? Celda.texto(MES.format(YearMonth.parse(fila.fecha()))) : new Fecha(LocalDate.parse(fila.fecha())),
						new Numero(fila.pedidos()), (Celda) new Dinero(fila.ingresos()))).toList(),
				sinVentasTexto)));
		hojas.add(hoja("Por día de la semana", new Seccion("Ventas por día de la semana", List.of(),
				List.of("Día", "Pedidos", "Ingresos"),
				sinVentas ? List.of() : ventas.porDiaSemana().stream().map(fila -> List.of(
						Celda.texto(DIAS.get(fila.dia() - 1)), new Numero(fila.pedidos()),
						(Celda) new Dinero(fila.ingresos()))).toList(),
				sinVentasTexto)));
		hojas.add(hoja("Por hora", new Seccion("Ventas por hora del pago", List.of(),
				List.of("Hora", "Pedidos", "Ingresos"),
				sinVentas ? List.of() : ventas.porHora().stream().map(fila -> List.of(
						Celda.texto("%02d:00".formatted(fila.hora())), new Numero(fila.pedidos()),
						(Celda) new Dinero(fila.ingresos()))).toList(),
				sinVentasTexto)));
		hojas.add(hoja("Por categoría", new Seccion("Ventas por categoría", List.of(),
				List.of("Categoría", "Pedidos", "Ingresos", "Margen"),
				sinVentas ? List.of() : ventas.porCategoria().stream().map(fila -> List.of(
						Celda.texto(fila.categoria()), new Numero(fila.pedidos()), new Dinero(fila.ingresos()),
						dineroO(fila.margen(), SIN_COSTO))).toList(),
				sinVentasTexto)));
		// RF-15 de HU-18: no depende del filtro por tipo
		boolean hayDeAlgunTipo = ventas.porTipo().stream().anyMatch(fila -> fila.pedidos() > 0);
		hojas.add(hoja("Por tipo de cliente", new Seccion("Ventas por tipo de cliente", List.of(),
				List.of("Tipo", "Pedidos", "Ingresos", "Promedio por pedido"),
				hayDeAlgunTipo ? ventas.porTipo().stream().map(fila -> List.of(Celda.texto(tipo(fila.tipo())),
						new Numero(fila.pedidos()), new Dinero(fila.ingresos()), dineroO(fila.promedio(), NADA)))
						.toList() : List.of(),
				"No hubo ventas en este período.")));
		hojas.add(hoja("Más vendidos", vendidos("Productos más vendidos", reporte.masVendidos(), sinVentasTexto)));
		hojas.add(hoja("Menos vendidos",
				vendidos("Productos menos vendidos", reporte.menosVendidos(), "No hay productos a la venta.")));
		hojas.add(hoja("Sin venderse", sinVenta(reporte.sinVenta(), reporte.dias())));
		hojas.add(new Hoja("Pedidos perdidos", List.of(perdidos(reporte.perdidos()),
				productosPerdidos(reporte.productosPerdidos()))));
		hojas.add(hoja("Clientes inactivos", clientes(reporte.clientesInactivos(), reporte.dias())));
		return hojas;
	}

	private static Hoja hoja(String nombre, Seccion seccion) {
		return new Hoja(nombre, List.of(seccion));
	}

	// RF-5
	private static Seccion encabezado(ReporteCompleto reporte) {
		PeriodoResponse periodo = reporte.ventas().periodo();
		return new Seccion(MARCA, List.of(), List.of(), List.of(
				par("Período", rango(periodo.desde(), periodo.hasta())),
				par("Comparado con", rango(periodo.anteriorDesde(), periodo.anteriorHasta())),
				par("Tipo de cliente", reporte.tipo() == null ? "Todos" : tipo(reporte.tipo())),
				par("Sin venderse y clientes inactivos", "Más de %d días".formatted(reporte.dias())),
				List.of(Celda.texto("Generado"), new FechaHora(reporte.generado())),
				par("Exportado por", reporte.autor())), "");
	}

	private static List<Celda> par(String dato, String valor) {
		return List.of(Celda.texto(dato), Celda.texto(valor));
	}

	private static String rango(LocalDate desde, LocalDate hasta) {
		return Celda.FECHA.format(desde) + " al " + Celda.FECHA.format(hasta);
	}

	// HU-18 RF-6 a RF-8, cada cifra con la anterior y la diferencia
	private static Seccion cifras(VentasResponse ventas, boolean sinVentas, String sinVentasTexto) {
		Totales actual = ventas.actual();
		Totales anterior = ventas.anterior();
		List<List<Celda>> filas = List.of(
				List.of(Celda.texto("Ingresos"), new Dinero(actual.ingresos()), new Dinero(anterior.ingresos()),
						diferencia(actual.ingresos(), anterior.ingresos(), "sin ventas antes", true)),
				List.of(Celda.texto("Pedidos vendidos"), new Numero(actual.pedidos()), new Numero(anterior.pedidos()),
						diferencia(BigDecimal.valueOf(actual.pedidos()), BigDecimal.valueOf(anterior.pedidos()),
								"sin ventas antes", true)),
				List.of(Celda.texto("Promedio por pedido"), dineroO(actual.promedio(), NADA),
						dineroO(anterior.promedio(), NADA),
						diferencia(actual.promedio(), anterior.promedio(), "sin ventas antes", true)),
				List.of(Celda.texto("Margen"), dineroO(actual.margen(), "Sin costo cargado"),
						dineroO(anterior.margen(), "Sin costo cargado"),
						diferencia(actual.margen(), anterior.margen(), "sin margen antes", false)),
				List.of(Celda.texto("Margen sobre lo vendido con costo"), porcentajeO(actual.margenPorcentaje()),
						porcentajeO(anterior.margenPorcentaje()), Celda.texto("")),
				List.of(Celda.texto("Vendido sin costo cargado"), new Dinero(actual.vendidoSinCosto()),
						new Dinero(anterior.vendidoSinCosto()), Celda.texto("")),
				List.of(Celda.texto("Pagos a devolver (pedidos)"), new Numero(actual.devolverCantidad()),
						new Numero(anterior.devolverCantidad()), Celda.texto("")),
				List.of(Celda.texto("Pagos a devolver (importe)"), new Dinero(actual.devolverImporte()),
						new Dinero(anterior.devolverImporte()),
						diferencia(actual.devolverImporte(), anterior.devolverImporte(),
								"sin pagos a devolver antes", true)));
		return new Seccion("Cifras del período", sinVentas ? List.of(sinVentasTexto) : List.of(),
				List.of("Cifra", "Este período", "Período anterior", "Diferencia"), filas, "");
	}

	// Como la pantalla (HU-18 RF-7): sin cifra antes, lo dice; con un cero antes no hay porcentaje, salvo que el
	// cero sea un dato (un margen de Bs 0)
	private static Celda diferencia(BigDecimal actual, BigDecimal anterior, String sinAntes, boolean ceroEsSinDatos) {
		if (anterior == null || (anterior.signum() == 0 && ceroEsSinDatos)) {
			return Celda.texto(sinAntes);
		}
		if (actual == null || anterior.signum() == 0) {
			return Celda.texto("");
		}
		return new Porcentaje(actual.subtract(anterior).multiply(BigDecimal.valueOf(100)).divide(anterior.abs(), 1,
				RoundingMode.HALF_UP));
	}

	private static Celda dineroO(BigDecimal valor, String sinValor) {
		return valor == null ? Celda.texto(sinValor) : new Dinero(valor);
	}

	private static Celda porcentajeO(BigDecimal valor) {
		return valor == null ? Celda.texto(NADA) : new Porcentaje(valor);
	}

	private static Numero cantidad(BigDecimal valor, UnidadVenta unidad) {
		return new Numero(valor, unidad.decimales());
	}

	// HU-18 RF-11 y RF-12
	private static Seccion vendidos(String titulo, List<ProductoVendido> productos, String vacio) {
		return new Seccion(titulo, List.of(),
				List.of("Código", "Producto", "Unidad", "Cantidad", "Importe", "Margen", "Estado"),
				productos.stream().map(fila -> List.of(Celda.texto(fila.codigo()), Celda.texto(fila.nombre()),
						Celda.texto(unidad(fila.unidad())), cantidad(fila.cantidad(), fila.unidad()),
						new Dinero(fila.importe()), dineroO(fila.margen(), SIN_COSTO),
						Celda.texto(fila.activo() ? "Activo" : "Inactivo"))).toList(),
				vacio);
	}

	// HU-18 RF-13
	private static Seccion sinVenta(List<ProductoSinVenta> productos, int dias) {
		return new Seccion("Productos sin venderse hace más de %d días".formatted(dias),
				List.of("El dinero inmovilizado es stock × costo, o stock × precio si el producto no tiene costo."),
				List.of("Código", "Producto", "Unidad", "Stock", "Precio", "Costo", "Última venta",
						"Días sin venderse", "Inmovilizado", "Calculado con"),
				productos.stream().map(fila -> List.of(Celda.texto(fila.codigo()), Celda.texto(fila.nombre()),
						Celda.texto(unidad(fila.unidad())), cantidad(fila.stock(), fila.unidad()),
						new Dinero(fila.precio()), dineroO(fila.costo(), SIN_COSTO),
						fila.ultimaVenta() == null ? Celda.texto("Nunca") : new Fecha(fila.ultimaVenta().toLocalDate()),
						new Numero(fila.diasSinVenta()), new Dinero(fila.inmovilizado()),
						Celda.texto(fila.inmovilizadoAPrecio() ? "precio" : "costo"))).toList(),
				"Ningún producto lleva más de %d días sin venderse.".formatted(dias));
	}

	// HU-18 RF-17
	private static Seccion perdidos(ResumenDePerdidos perdidos) {
		return new Seccion("Pedidos perdidos", List.of(
				"Vencidos o cancelados entre los confirmados del período. La proporción no cuenta los que esperan el pago ni los pagos a devolver."),
				List.of(), List.of(
						List.of(Celda.texto("Pedidos perdidos"), new Numero(perdidos.cantidad())),
						List.of(Celda.texto("Importe perdido"), new Dinero(perdidos.importe())),
						List.of(Celda.texto("Vendidos de los confirmados"),
								Celda.texto("%d de %d".formatted(perdidos.vendidos(), perdidos.confirmados()))),
						List.of(Celda.texto("Proporción vendida"), porcentajeO(perdidos.proporcion()))),
				"");
	}

	// HU-18 RF-18
	private static Seccion productosPerdidos(List<ProductoPerdido> productos) {
		return new Seccion("Productos de los pedidos perdidos", List.of(),
				List.of("Código", "Producto", "Unidad", "Cantidad", "Importe"),
				productos.stream().map(fila -> List.of(Celda.texto(fila.codigo()), Celda.texto(fila.nombre()),
						Celda.texto(unidad(fila.unidad())), cantidad(fila.cantidad(), fila.unidad()),
						(Celda) new Dinero(fila.importe()))).toList(),
				"No hubo pedidos perdidos en este período.");
	}

	// HU-18 RF-16, con el contacto para llamarlos (RF-8)
	private static Seccion clientes(List<ClienteInactivo> clientes, int dias) {
		return new Seccion("Clientes que no compran hace más de %d días".formatted(dias),
				List.of(AVISO_DATOS_PERSONALES),
				List.of("Cliente", "Correo", "Teléfono", "Tipo", "Última compra", "Pedidos", "Total comprado"),
				clientes.stream().map(fila -> List.of(Celda.texto(fila.nombre() + " " + fila.apellido()),
						Celda.texto(fila.correo()), Celda.texto(fila.telefono()),
						Celda.texto(tipo(fila.tipo())), new Fecha(fila.ultimaCompra().toLocalDate()),
						new Numero(fila.pedidos()), (Celda) new Dinero(fila.total()))).toList(),
				"Ningún cliente dejó de comprar hace más de %d días.".formatted(dias));
	}
}
