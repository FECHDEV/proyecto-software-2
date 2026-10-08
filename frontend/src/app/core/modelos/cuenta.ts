// Espejo de DatosPersonalesRequest y CambioContrasenaRequest del backend
// (backend/src/main/java/bo/edu/uagrm/tienda/dto/). Correo, rol y
// estado no se modifican desde Mis datos, así que no están acá.
export interface DatosPersonales {
	nombre: string;
	apellido: string;
	telefono: string;
}

export interface CambioDeContrasena {
	contrasenaActual: string;
	contrasenaNueva: string;
}
