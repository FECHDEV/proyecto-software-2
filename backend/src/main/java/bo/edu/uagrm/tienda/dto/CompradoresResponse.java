package bo.edu.uagrm.tienda.dto;

import java.util.List;

// El comprador por nombre (HU-23 RF-25): hasta 10 cuentas, con el correo enmascarado, y si hay más con ese texto
public record CompradoresResponse(List<CompradorResponse> cuentas, boolean hayMas) {

	public static final CompradoresResponse NINGUNO = new CompradoresResponse(List.of(), false);
}
