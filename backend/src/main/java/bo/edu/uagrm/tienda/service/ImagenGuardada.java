package bo.edu.uagrm.tienda.service;

// Una imagen leída de AlmacenImagenes, lista para servir. El nombre es el del archivo guardado: cambia con cada
// subida, así que identifica la versión
public record ImagenGuardada(byte[] contenido, FormatoImagen formato, String nombre) {
}
