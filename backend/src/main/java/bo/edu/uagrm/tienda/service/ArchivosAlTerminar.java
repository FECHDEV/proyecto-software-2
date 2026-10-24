package bo.edu.uagrm.tienda.service;

// Borra archivos de imágenes cuando termina la transacción: al confirmar, el primero; al revertir, el segundo. Así
// un fallo nunca deja una fila apuntando a un archivo borrado. Sin transacción activa (pruebas unitarias) se aplica
// enseguida, como si se confirmara
final class ArchivosAlTerminar {

	private ArchivosAlTerminar() {
	}

	static void borrar(AlmacenImagenes almacen, String siSeConfirma, String siSeRevierte) {
		TrasElCommit.alTerminar(() -> borrarSiHay(almacen, siSeConfirma), () -> borrarSiHay(almacen, siSeRevierte));
	}

	private static void borrarSiHay(AlmacenImagenes almacen, String imagen) {
		if (imagen != null) {
			almacen.borrar(imagen);
		}
	}
}
