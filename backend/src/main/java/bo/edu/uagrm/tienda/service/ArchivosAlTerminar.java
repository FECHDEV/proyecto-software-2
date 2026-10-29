package bo.edu.uagrm.tienda.service;

import java.util.List;

// Borra archivos de imágenes cuando termina la transacción: al confirmar, el primero; al revertir, el segundo. Así
// un fallo nunca deja una fila apuntando a un archivo borrado. Sin transacción activa (pruebas unitarias) se aplica
// enseguida, como si se confirmara
final class ArchivosAlTerminar {

	private ArchivosAlTerminar() {
	}

	static void borrar(AlmacenImagenes almacen, String siSeConfirma, String siSeRevierte) {
		TrasElCommit.alTerminar(() -> borrarSiHay(almacen, siSeConfirma), () -> borrarSiHay(almacen, siSeRevierte));
	}

	// Con varios archivos (una foto y sus versiones, HU-22). Las listas se leen al terminar: la de lo que se revierte
	// se puede registrar antes de escribir y llenar a medida que se guarda, así un fallo a mitad borra lo que alcanzó
	// a escribirse
	static void borrar(AlmacenImagenes almacen, List<String> siSeConfirma, List<String> siSeRevierte) {
		TrasElCommit.alTerminar(() -> siSeConfirma.forEach(almacen::borrar), () -> siSeRevierte.forEach(almacen::borrar));
	}

	private static void borrarSiHay(AlmacenImagenes almacen, String imagen) {
		if (imagen != null) {
			almacen.borrar(imagen);
		}
	}
}
