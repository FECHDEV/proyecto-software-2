package bo.edu.uagrm.tienda.dto;

import org.springframework.data.domain.Sort;

// Los órdenes del catálogo (HU-09 RF-10). Los agotados van al final en todos: lo pone la consulta, antes de este
// orden. El número del producto desempata, para que una página no repita ni pierda productos
public enum OrdenCatalogo {

	// Por precio, el precio con descuento lo ordena la consulta (HU-24 RF-13, porPrecio); acá solo el desempate
	RECIENTES(Sort.by(Sort.Direction.DESC, "fechaAlta", "idProducto"), 0),
	PRECIO_ASC(Sort.by(Sort.Order.desc("idProducto")), 1),
	PRECIO_DESC(Sort.by(Sort.Order.desc("idProducto")), -1),
	NOMBRE(Sort.by(Sort.Order.asc("nombre"), Sort.Order.asc("idProducto")), 0);

	private final Sort orden;
	private final int porPrecio;

	OrdenCatalogo(Sort orden, int porPrecio) {
		this.orden = orden;
		this.porPrecio = porPrecio;
	}

	public Sort orden() {
		return orden;
	}

	// 1 de menor a mayor precio con descuento, −1 de mayor a menor, 0 sin orden por precio (ProductoRepository)
	public int porPrecio() {
		return porPrecio;
	}
}
