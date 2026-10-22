package bo.edu.uagrm.tienda.service.exportar;

import java.util.List;

// Una sección del reporte exportado (HU-19): un título, notas debajo, y una tabla, o el texto de vacío si no tiene
// filas (RF-9). Sin columnas, las filas son pares «dato, valor» sin fila de títulos
record Seccion(String titulo, List<String> notas, List<String> columnas, List<List<Celda>> filas, String vacio) {

	// Una hoja del Excel, o un bloque del PDF con su nombre como título (RF-10, RF-11)
	record Hoja(String nombre, List<Seccion> secciones) {
	}
}
