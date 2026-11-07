import {
	afterNextRender,
	ChangeDetectionStrategy,
	Component,
	computed,
	effect,
	inject,
	Injector,
	NgZone,
	signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { LucideTrash2 } from '@lucide/angular';

import { mensajeDeError } from '../../core/api/mensajes-error';
import { VentasService } from '../../core/api/ventas.service';
import { comoError, ErrorApi } from '../../core/modelos/error-api';
import { DECIMALES_DE_UNIDAD } from '../../core/modelos/inventario';
import { AVISOS_DE_RECHAZO, ResultadoSimulado } from '../../core/modelos/pedido';
import { unidadPara } from '../../core/modelos/producto';
import {
	Comprador,
	MAXIMO_LINEAS_DE_VENTA,
	MAXIMO_RECIBIDO,
	MedioPago,
	ProductoParaVenta,
	Venta,
} from '../../core/modelos/venta';
import { cantidadConPaso, leerCantidad, problemaDeCantidad } from '../../shared/formato/cantidades';
import { cantidad, precio } from '../../shared/formato/precios';
import { PasoDeCantidad } from '../../shared/paso-de-cantidad/paso-de-cantidad';
import { BusquedaDeProductos } from './busqueda-de-productos';
import { QrDeVenta } from './qr-de-venta';

// Cada cuánto pregunta la pantalla si llegó el pago mientras el QR está a la vista (RF-8), como en línea
export const INTERVALO_DE_CONSULTA = 5000;
const MAXIMA_CANTIDAD = 999;

// Una línea de la venta como está en la pantalla: el producto que trajo la búsqueda y la cantidad como se escribió
interface LineaEnVenta {
	producto: ProductoParaVenta;
	texto: string;
}

// «Nueva venta» (HU-23 RF-1 a RF-14): el Empleado o el Administrador arma la
// venta del mostrador, elige al comprador y cobra en efectivo o con QR. El
// precio, el stock y el total los decide el servidor: la pantalla los muestra y
// manda el total que vio, que solo se compara (RF-14). Las líneas viven en la
// pantalla hasta cobrar: con QR, cancelar las deja para cobrar de otra forma
@Component({
	selector: 'app-venta',
	imports: [BusquedaDeProductos, LucideTrash2, PasoDeCantidad, QrDeVenta],
	templateUrl: './venta.page.html',
	styleUrl: './venta.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VentaPage {
	private readonly ventas = inject(VentasService);
	private readonly router = inject(Router);
	private readonly zona = inject(NgZone);
	private readonly injector = inject(Injector);

	protected readonly precio = precio;
	protected readonly cantidad = cantidad;
	protected readonly unidadPara = unidadPara;

	// --- Las líneas (RF-2, RF-3) ---

	protected readonly lineas = signal<readonly LineaEnVenta[]>([]);
	protected readonly avisoDeLineas = signal('');
	// Por producto: lo que dijo el servidor al rechazar (RF-12)
	protected readonly rechazos = signal<ReadonlyMap<number, string>>(new Map());

	// En centavos enteros: sumar decimales en JavaScript deja restos (85 + 8,46 = 93,46000000000001)
	protected readonly total = computed(
		() => this.lineas().reduce((suma, linea) => suma + (centavos(linea) ?? 0), 0) / 100,
	);
	protected readonly hayProblemas = computed(() => this.lineas().some((linea) => this.problema(linea) !== null));

	// --- El comprador (RF-4) ---

	protected readonly correo = signal('');
	protected readonly comprador = signal<Comprador | null>(null);
	protected readonly buscandoComprador = signal(false);
	protected readonly errorDeComprador = signal('');

	// --- El cobro (RF-5 a RF-9) ---

	protected readonly recibido = signal('');
	protected readonly recibidoLeido = computed(() => leerRecibido(this.recibido()));
	protected readonly cambio = computed(() => {
		const recibido = this.recibidoLeido();
		return recibido === null ? null : redondear(recibido - this.total());
	});
	protected readonly registrando = signal(false);
	protected readonly error = signal('');
	protected readonly errorDeRecibido = signal('');
	// La venta con QR que espera el pago; null mientras se arma
	protected readonly conQr = signal<Venta | null>(null);
	protected readonly segundos = signal(0);
	private readonly consultando = signal(false);
	protected readonly cancelando = signal(false);
	protected readonly simulando = signal(false);
	protected readonly avisoDelPago = signal('');

	constructor() {
		// RF-8: mientras el QR está a la vista, la pantalla pregunta sola y cuenta
		// el tiempo que queda. Fuera de la zona, como el pago en línea
		effect((alLimpiar) => {
			const venta = this.conQr();
			if (venta === null) {
				return;
			}
			this.segundos.set(venta.segundosParaPagar);
			const id = venta.idPedido;
			const consulta = this.zona.runOutsideAngular(() =>
				setInterval(() => void this.consultar(id, false), INTERVALO_DE_CONSULTA),
			);
			const cuenta = this.zona.runOutsideAngular(() =>
				setInterval(() => this.segundos.update((quedan) => Math.max(0, quedan - 1)), 1000),
			);
			alLimpiar(() => {
				clearInterval(consulta);
				clearInterval(cuenta);
			});
		});
	}

	// RF-2: el mismo producto suma uno a su línea; uno nuevo entra con 1
	protected agregar(producto: ProductoParaVenta): void {
		if (this.bloqueada()) {
			return;
		}
		this.avisoDeLineas.set('');
		const lineas = this.lineas();
		const indice = lineas.findIndex((linea) => linea.producto.idProducto === producto.idProducto);
		if (indice >= 0) {
			this.pasar(indice, 1);
			return;
		}
		if (lineas.length >= MAXIMO_LINEAS_DE_VENTA) {
			this.avisoDeLineas.set(`La venta admite hasta ${MAXIMO_LINEAS_DE_VENTA} productos.`);
			return;
		}
		this.lineas.set([...lineas, { producto, texto: '1' }]);
		this.olvidarRechazo(producto.idProducto);
	}

	// --- Líneas ---

	protected cambiar(indice: number, texto: string): void {
		this.actualizar(indice, texto);
	}

	protected conPaso(linea: LineaEnVenta, paso: 1 | -1): number | null {
		return cantidadConPaso(leerCantidad(linea.texto) ?? 0, paso, decimales(linea), MAXIMA_CANTIDAD);
	}

	protected pasar(indice: number, paso: 1 | -1): void {
		const nueva = this.conPaso(this.lineas()[indice], paso);
		if (nueva !== null) {
			this.actualizar(indice, cantidad(nueva));
		}
	}

	protected quitar(indice: number): void {
		if (this.bloqueada()) {
			return;
		}
		const quitada = this.lineas()[indice];
		this.lineas.set(this.lineas().filter((_, cada) => cada !== indice));
		this.olvidarRechazo(quitada.producto.idProducto);
	}

	// Por qué la cantidad escrita no sirve; null si sirve (RF-2, RF-13)
	protected problema(linea: LineaEnVenta): string | null {
		if (linea.texto.trim() === '') {
			return 'Escriba la cantidad.';
		}
		return problemaDeCantidad(linea.texto, { decimales: decimales(linea), maximo: MAXIMA_CANTIDAD, positiva: true });
	}

	// RF-3: pide más de lo que hay disponible
	protected pasaDelDisponible(linea: LineaEnVenta): boolean {
		const pedida = leerCantidad(linea.texto);
		return pedida !== null && pedida > linea.producto.disponible;
	}

	protected subtotal(linea: LineaEnVenta): number | null {
		return subtotal(linea);
	}

	protected disponible(producto: ProductoParaVenta): string {
		return `${cantidad(producto.disponible)} ${unidadPara(producto.unidad, producto.disponible)}`;
	}

	// --- Comprador ---

	protected async buscarComprador(): Promise<void> {
		const correo = this.correo().trim();
		if (this.buscandoComprador() || this.bloqueada()) {
			return;
		}
		if (correo === '') {
			this.errorDeComprador.set('Escriba el correo del cliente.');
			return;
		}
		this.errorDeComprador.set('');
		this.buscandoComprador.set(true);
		try {
			this.comprador.set(await this.ventas.buscarCliente(correo));
			this.correo.set('');
		} catch (rechazo: unknown) {
			const error = comoError(rechazo);
			this.errorDeComprador.set(
				error.codigo === 'USUARIO_NO_ENCONTRADO'
					? 'No hay una cuenta de cliente activa con ese correo.'
					: mensajeDeError(error),
			);
		} finally {
			this.buscandoComprador.set(false);
		}
	}

	protected aConsumidorFinal(): void {
		if (!this.bloqueada()) {
			this.comprador.set(null);
		}
	}

	// --- Cobro ---

	// RF-5, RF-6: lo recibido no puede ser menor que el total
	protected async cobrarEnEfectivo(): Promise<void> {
		this.errorDeRecibido.set('');
		const recibido = this.recibidoLeido();
		if (recibido === null) {
			this.errorDeRecibido.set('Escriba lo recibido con números, por ejemplo 100 o 100,50.');
			return;
		}
		if (recibido > MAXIMO_RECIBIDO) {
			this.errorDeRecibido.set(`Lo recibido admite hasta ${precio(MAXIMO_RECIBIDO)}.`);
			return;
		}
		if (recibido < this.total()) {
			this.errorDeRecibido.set('Lo recibido no alcanza para el total.');
			return;
		}
		const venta = await this.registrar('EFECTIVO', recibido);
		if (venta !== null) {
			void this.router.navigate(['/ventas', venta.idPedido]);
		}
	}

	// RF-7: reserva el stock y muestra el QR; si el sistema de pago no lo generó, la venta ya se canceló
	protected async cobrarConQr(): Promise<void> {
		const venta = await this.registrar('QR', null);
		if (venta !== null) {
			this.avisoDelPago.set('');
			this.conQr.set(venta);
		}
	}

	// RF-8: «Ya pagó» pregunta en el acto
	protected yaPago(): void {
		const venta = this.conQr();
		if (venta !== null) {
			void this.consultar(venta.idPedido, true);
		}
	}

	// RF-9: cancela y deja las líneas para cobrar de otra forma
	protected async cancelarQr(): Promise<void> {
		const venta = this.conQr();
		if (venta === null || this.cancelando()) {
			return;
		}
		this.cancelando.set(true);
		try {
			const cancelada = await this.ventas.cancelar(venta.idPedido);
			this.alCambiar(cancelada);
		} catch (rechazo: unknown) {
			const error = comoError(rechazo);
			// Ya se pagó, venció o canceló: se pregunta cómo quedó
			if (error.codigo === 'PEDIDO_NO_PENDIENTE') {
				await this.consultar(venta.idPedido, false);
			} else {
				this.avisoDelPago.set(mensajeDeError(error));
			}
		} finally {
			this.cancelando.set(false);
		}
	}

	// Herramienta de pruebas, solo con el simulador: lo que haría el banco, y después la consulta
	protected async simular(resultado: ResultadoSimulado): Promise<void> {
		const venta = this.conQr();
		if (venta === null || this.simulando()) {
			return;
		}
		this.simulando.set(true);
		try {
			await this.ventas.simular(venta.idPedido, resultado);
		} catch (rechazo: unknown) {
			this.avisoDelPago.set(mensajeDeError(comoError(rechazo)));
			return;
		} finally {
			this.simulando.set(false);
		}
		await this.consultar(venta.idPedido, false);
	}

	// Mientras espera una respuesta o hay un QR a la vista, la venta no se cambia
	protected bloqueada(): boolean {
		return this.registrando() || this.conQr() !== null;
	}

	protected puedeCobrar(): boolean {
		return this.lineas().length > 0 && !this.hayProblemas() && !this.bloqueada();
	}

	// Un segundo toque mientras espera no registra dos veces (casos límite)
	private async registrar(medio: MedioPago, recibido: number | null): Promise<Venta | null> {
		if (!this.puedeCobrar()) {
			return null;
		}
		this.error.set('');
		this.rechazos.set(new Map());
		this.registrando.set(true);
		const total = this.total();
		try {
			return await this.ventas.registrar({
				lineas: this.lineas().map((linea) => ({
					idProducto: linea.producto.idProducto,
					cantidad: leerCantidad(linea.texto) ?? 0,
				})),
				idCliente: this.comprador()?.idUsuario ?? null,
				medio,
				recibido,
				totalEsperado: total,
			});
		} catch (rechazo: unknown) {
			await this.rechazada(comoError(rechazo));
			return null;
		} finally {
			this.registrando.set(false);
		}
	}

	// RF-12: cada línea dice por qué y cuánto hay; RF-14: se traen los precios de hoy
	private async rechazada(error: ErrorApi): Promise<void> {
		if (error.codigo === 'STOCK_INSUFICIENTE' && error.lineas !== undefined) {
			const rechazos = new Map<number, string>();
			for (const linea of error.lineas) {
				const producto = this.lineas().find((cada) => cada.producto.idProducto === linea.idProducto)?.producto;
				const disponible =
					linea.motivo === 'NO_ALCANZA' && producto !== undefined && linea.disponible !== undefined
						? `: hay ${cantidad(linea.disponible)} ${unidadPara(producto.unidad, linea.disponible)}`
						: '';
				rechazos.set(linea.idProducto, `${AVISOS_DE_RECHAZO[linea.motivo]}${disponible}.`);
				if (producto !== undefined && linea.disponible !== undefined) {
					this.conDisponible(linea.idProducto, linea.disponible);
				}
			}
			this.rechazos.set(rechazos);
			this.error.set('Algunos productos no se pueden vender así. Corrija los marcados.');
			// La primera línea marcada recibe el foco: en el celular queda lejos del botón (DESIGN.md → Error)
			const primera = error.lineas[0]?.idProducto;
			afterNextRender(() => document.getElementById(`cantidad-${primera}`)?.focus(), { injector: this.injector });
			return;
		}
		if (error.codigo === 'TOTAL_CAMBIADO') {
			await this.actualizarPrecios();
			this.error.set('Cambió un precio mientras armaba la venta. Revise el total nuevo y cobre otra vez.');
			return;
		}
		this.error.set(mensajeDeError(error));
	}

	private async actualizarPrecios(): Promise<void> {
		const lineas = await Promise.all(
			this.lineas().map(async (linea) => {
				try {
					const deHoy = await this.ventas.precioDeHoy(linea.producto.idProducto);
					return { ...linea, producto: { ...linea.producto, precio: deHoy } };
				} catch {
					return linea;
				}
			}),
		);
		this.lineas.set(lineas);
	}

	// Una consulta a la vez. Las automáticas no muestran un error de red: se vuelve a preguntar en la próxima vuelta
	private async consultar(idPedido: number, pedidaPorQuienVende: boolean): Promise<void> {
		if (this.consultando()) {
			return;
		}
		this.consultando.set(true);
		if (pedidaPorQuienVende) {
			this.avisoDelPago.set('');
		}
		try {
			const venta = await this.ventas.consultar(idPedido);
			if (this.conQr()?.idPedido !== idPedido) {
				return;
			}
			if (pedidaPorQuienVende && venta.estado === 'PENDIENTE_PAGO') {
				this.avisoDelPago.set('Todavía no llegó el pago. Si el cliente ya pagó, espere unos segundos.');
			}
			this.alCambiar(venta);
		} catch (rechazo: unknown) {
			if (pedidaPorQuienVende) {
				this.avisoDelPago.set(mensajeDeError(comoError(rechazo)));
			}
		} finally {
			this.consultando.set(false);
		}
	}

	// Cobrada, al comprobante; cancelada o vencida, de vuelta a armar con las mismas líneas
	private alCambiar(venta: Venta): void {
		if (venta.estado === 'ENTREGADO' || venta.estado === 'PAGADO') {
			this.conQr.set(null);
			void this.router.navigate(['/ventas', venta.idPedido]);
			return;
		}
		if (venta.estado !== 'PENDIENTE_PAGO') {
			this.conQr.set(null);
			this.error.set(
				venta.estado === 'VENCIDO'
					? 'El QR venció sin pago y el stock volvió. Puede cobrar otra vez.'
					: 'Se canceló el cobro con QR. Puede cobrar en efectivo o generar otro QR.',
			);
			return;
		}
		this.conQr.set(venta);
	}

	private actualizar(indice: number, texto: string): void {
		if (this.bloqueada()) {
			return;
		}
		const lineas = [...this.lineas()];
		lineas[indice] = { ...lineas[indice], texto };
		this.lineas.set(lineas);
		this.olvidarRechazo(lineas[indice].producto.idProducto);
	}

	private conDisponible(idProducto: number, disponible: number): void {
		this.lineas.set(
			this.lineas().map((linea) =>
				linea.producto.idProducto === idProducto ? { ...linea, producto: { ...linea.producto, disponible } } : linea,
			),
		);
	}

	private olvidarRechazo(idProducto: number): void {
		if (this.rechazos().has(idProducto)) {
			const rechazos = new Map(this.rechazos());
			rechazos.delete(idProducto);
			this.rechazos.set(rechazos);
		}
	}
}

function decimales(linea: LineaEnVenta): number {
	return DECIMALES_DE_UNIDAD[linea.producto.unidad];
}

function subtotal(linea: LineaEnVenta): number | null {
	const enCentavos = centavos(linea);
	return enCentavos === null ? null : enCentavos / 100;
}

// Como el servidor (LineaPedido): precio × cantidad redondeado a centavos, el medio centavo hacia arriba (HALF_UP).
// Con enteros, porque 4,50 × 0,69 = 3,105 da 3,1049999… en decimales de JavaScript y bajaba a 3,10. El precio y la
// cantidad tienen hasta dos decimales, y el producto entra holgado en un entero exacto
function centavos(linea: LineaEnVenta): number | null {
	const pedida = leerCantidad(linea.texto);
	if (pedida === null) {
		return null;
	}
	const diezMilesimos = Math.round(linea.producto.precio * 100) * Math.round(pedida * 100);
	return Math.floor((diezMilesimos + 50) / 100);
}

// Lo recibido como lo escribe quien cobra: «100», «100,50» o «100.5». null si no tiene esa forma; el tope de
// 999 999,99 (RF-13) lo revisa el cobro, con su propio aviso
function leerRecibido(texto: string): number | null {
	const limpio = texto.trim();
	return /^\d{1,9}([.,]\d{1,2})?$/.test(limpio) ? Number(limpio.replace(',', '.')) : null;
}

function redondear(valor: number): number {
	return Math.round((valor + Number.EPSILON) * 100) / 100;
}
