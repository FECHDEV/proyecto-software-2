import { Cobro, EstadoDePedido, LineaDePedido } from './pedido';
import { UnidadVenta } from './producto';

// Espejo de VentaRequest, VentaResponse, ProductoVentaResponse y CompradorResponse del backend (HU-23)

// Dónde se vendió: la tienda en línea o el mostrador del local
export type Canal = 'EN_LINEA' | 'TIENDA';

export const NOMBRES_DE_CANAL: Record<Canal, string> = {
	EN_LINEA: 'En línea',
	TIENDA: 'En tienda',
};

// El efectivo lo confirma quien recibe el dinero; el QR, el sistema de pago
export type MedioPago = 'EFECTIVO' | 'QR';

export const NOMBRES_DE_MEDIO: Record<MedioPago, string> = {
	EFECTIVO: 'Efectivo',
	QR: 'QR',
};

// Como el carrito (HU-11): hasta 999 por línea y 50 líneas (RF-2)
export const MAXIMO_LINEAS_DE_VENTA = 50;
// En efectivo, lo recibido (RF-13)
export const MAXIMO_RECIBIDO = 999_999.99;

// Un resultado de la búsqueda de «Nueva venta» (RF-1, RF-3): el precio de hoy y el disponible
export interface ProductoParaVenta {
	idProducto: number;
	nombre: string;
	imagen: string | null;
	precio: number;
	unidad: UnidadVenta;
	disponible: number;
}

// La cuenta de Cliente a cuyo nombre queda la venta (RF-4)
export interface Comprador {
	idUsuario: number;
	nombre: string;
	correo: string;
}

// Lo que se manda al registrar (RF-5 a RF-7). idCliente null: consumidor final; recibido solo en efectivo
export interface SolicitudDeVenta {
	lineas: { idProducto: number; cantidad: number }[];
	idCliente: number | null;
	medio: MedioPago;
	recibido: number | null;
	totalEsperado: number;
}

// La venta y su comprobante (RF-6 a RF-9, RF-15, RF-19). anulable: si hoy se puede anular; el botón, solo para el
// Administrador
export interface Venta {
	idPedido: number;
	estado: EstadoDePedido;
	confirmado: string;
	vence: string;
	segundosParaPagar: number;
	pagado: string | null;
	comprador: Comprador | null;
	vendidoPor: string;
	lineas: LineaDePedido[];
	total: number;
	medio: MedioPago;
	recibido: number | null;
	cambio: number | null;
	cobro: Cobro | null;
	anulado: string | null;
	anuladoPor: string | null;
	motivoAnulacion: string | null;
	anulable: boolean;
}
