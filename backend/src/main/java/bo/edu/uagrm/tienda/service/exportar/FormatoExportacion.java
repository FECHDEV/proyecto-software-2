package bo.edu.uagrm.tienda.service.exportar;

import java.util.Arrays;

import bo.edu.uagrm.tienda.exception.FormatoInvalidoException;

// Los formatos en que se exporta el reporte (HU-19), con su extensión y su tipo de contenido
public enum FormatoExportacion {

	XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
	PDF("pdf", "application/pdf");

	private final String extension;
	private final String tipoDeContenido;

	FormatoExportacion(String extension, String tipoDeContenido) {
		this.extension = extension;
		this.tipoDeContenido = tipoDeContenido;
	}

	// Sin importar mayúsculas; cualquier otro, o ninguno, es 400 FORMATO_INVALIDO (RF-3)
	public static FormatoExportacion de(String formato) {
		return Arrays.stream(values()).filter(cada -> formato != null && cada.extension.equalsIgnoreCase(formato.strip()))
				.findFirst().orElseThrow(FormatoInvalidoException::new);
	}

	public String extension() {
		return extension;
	}

	public String tipoDeContenido() {
		return tipoDeContenido;
	}
}
