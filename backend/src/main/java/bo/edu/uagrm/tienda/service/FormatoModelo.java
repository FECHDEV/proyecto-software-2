package bo.edu.uagrm.tienda.service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

// El modelo 3D de un producto (HU-15; decisiones.md → Visor 3D y modelos): un glTF binario versión 2, reconocido
// por su contenido como las imágenes. La cabecera declara el largo del archivo y el primer bloque es el JSON
public final class FormatoModelo {

	public static final String TIPO_DE_CONTENIDO = "model/gltf-binary";
	public static final String EXTENSION = "glb";
	public static final long MAXIMO = 3L * 1024 * 1024;

	// «glTF» y «JSON», leídos en little-endian
	private static final int MAGIA = 0x46546C67;
	private static final int BLOQUE_JSON = 0x4E4F534A;
	private static final int VERSION = 2;
	private static final int CABECERA = 12;
	private static final int CABECERA_DE_BLOQUE = 8;

	private FormatoModelo() {
	}

	public static boolean esGlb(byte[] contenido) {
		if (contenido.length < CABECERA + CABECERA_DE_BLOQUE) {
			return false;
		}
		ByteBuffer buffer = ByteBuffer.wrap(contenido).order(ByteOrder.LITTLE_ENDIAN);
		if (buffer.getInt() != MAGIA || buffer.getInt() != VERSION
				|| Integer.toUnsignedLong(buffer.getInt()) != contenido.length) {
			return false;
		}
		long largoDelJson = Integer.toUnsignedLong(buffer.getInt());
		return buffer.getInt() == BLOQUE_JSON && largoDelJson > 0
				&& largoDelJson <= contenido.length - CABECERA - CABECERA_DE_BLOQUE;
	}
}
