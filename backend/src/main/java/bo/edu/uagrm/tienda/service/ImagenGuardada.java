package bo.edu.uagrm.tienda.service;

// Una imagen leída de AlmacenImagenes, lista para servir. El nombre es el del archivo guardado: cambia con cada
// subida, así que identifica la versión. «servidaComoSePidio» es falso cuando se pidió una versión achicada que
// todavía no existe y va el original en su lugar (HU-22 RF-8): esa respuesta no se guarda en el navegador
public record ImagenGuardada(byte[] contenido, FormatoImagen formato, String nombre, boolean servidaComoSePidio) {

	public ImagenGuardada(byte[] contenido, FormatoImagen formato, String nombre) {
		this(contenido, formato, nombre, true);
	}
}
