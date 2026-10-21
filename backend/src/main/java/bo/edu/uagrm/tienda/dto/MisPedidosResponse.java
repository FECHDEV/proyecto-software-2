package bo.edu.uagrm.tienda.dto;

// «Mis pedidos» (HU-14): el que espera el pago aparte, para mostrarlo arriba (RF-2), o null; y la página de la lista,
// donde también está en su lugar (RF-1)
public record MisPedidosResponse(PedidoResumenResponse pendiente, PaginaResponse<PedidoResumenResponse> pagina) {
}
