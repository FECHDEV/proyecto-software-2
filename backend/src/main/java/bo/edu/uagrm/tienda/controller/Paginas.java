package bo.edu.uagrm.tienda.controller;

import java.math.BigInteger;

// La página pedida en la dirección de las listas de pedidos (HU-14 RF-8, HU-17 RF-12)
final class Paginas {

	private Paginas() {
	}

	// Solo dígitos, con signo opcional: lo demás es la primera página. Un número que no entra en un int es «después de
	// la última», como cualquier otro número grande
	static int numeroDePagina(String pagina) {
		if (pagina == null || !pagina.strip().matches("-?\\d{1,40}")) {
			return 0;
		}
		BigInteger numero = new BigInteger(pagina.strip());
		return numero.signum() < 0 ? 0 : numero.min(BigInteger.valueOf(Integer.MAX_VALUE)).intValue();
	}
}
