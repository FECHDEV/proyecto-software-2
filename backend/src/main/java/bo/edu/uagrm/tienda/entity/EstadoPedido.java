package bo.edu.uagrm.tienda.entity;

// Dónde está un pedido: espera el pago hasta que se paga, se cancela o vence (HU-12). Un pago que llega con el pedido
// vencido o cancelado lo revive como pagado, o lo deja para devolver si ya no alcanza el stock (HU-13 RF-9). Pagado, el
// personal lo entrega (HU-17)
public enum EstadoPedido {
	PENDIENTE_PAGO, PAGADO, CANCELADO, VENCIDO, PAGO_A_DEVOLVER, ENTREGADO
}
