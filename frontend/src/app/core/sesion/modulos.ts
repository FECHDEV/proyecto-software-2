import { Rol } from '../modelos/usuario';

// Quién entra a cada módulo del personal, en un solo lugar: las rutas de los módulos (rolGuard), el encabezado, el
// panel y el inicio de cada uno lo leen de acá (revisión total: estaba repetido en tres lugares). «panel» es a quién
// se le muestra y es su inicio: la ruta /panel es de todos con sesión. Ocultar según el rol es usabilidad: quien
// autoriza es el backend
const PERSONAL: readonly Rol[] = ['ADMINISTRADOR', 'EMPLEADO', 'CONTADOR'];

export const ROLES_DE = {
	panel: PERSONAL,
	usuarios: ['ADMINISTRADOR'],
	categorias: ['ADMINISTRADOR'],
	productos: ['ADMINISTRADOR'],
	// HU-08, HU-17: el Contador ve el inventario y los pedidos sin poder registrar
	inventario: PERSONAL,
	pedidos: PERSONAL,
	// HU-18 RF-1: el Empleado no
	reportes: ['ADMINISTRADOR', 'CONTADOR'],
	// HU-23 RF-23, RF-24: registran el Empleado y el Administrador; el Contador ve el comprobante desde Pedidos
	ventas: ['ADMINISTRADOR', 'EMPLEADO'],
} as const satisfies Record<string, readonly Rol[]>;

export type Modulo = keyof typeof ROLES_DE;

export function puedeEntrar(rol: Rol | null, modulo: Modulo): boolean {
	return rol !== null && (ROLES_DE[modulo] as readonly Rol[]).includes(rol);
}
