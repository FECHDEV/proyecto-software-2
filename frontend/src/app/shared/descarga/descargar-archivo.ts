// Guarda un archivo que llegó como Blob con el nombre dado: un enlace con
// `download` que no se agrega a la página. Firefox y Safari empiezan la
// descarga después del clic, así que la URL se suelta en el turno siguiente
export function descargarArchivo(archivo: Blob, nombre: string): void {
	const url = URL.createObjectURL(archivo);
	const enlace = document.createElement('a');
	enlace.href = url;
	enlace.download = nombre;
	enlace.click();
	setTimeout(() => URL.revokeObjectURL(url), 0);
}
