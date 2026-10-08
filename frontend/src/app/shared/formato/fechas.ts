// Formato de fechas para mostrar en pantalla, con Intl y no armado a mano.
// Se formatea en UTC a partir del día del calendario (AAAA-MM-DD): si se
// leyera en la zona local, medianoche en UTC ya es el día anterior en Bolivia.
const FECHA_CORTA = new Intl.DateTimeFormat('es-BO', {
	day: '2-digit',
	month: '2-digit',
	year: 'numeric',
	timeZone: 'UTC',
});

// Acepta una fecha (LocalDate) o una fecha con hora (LocalDateTime) del backend
export function fechaCorta(iso: string): string {
	return FECHA_CORTA.format(new Date(`${iso.slice(0, 10)}T00:00:00Z`));
}

// LocalTime llega como HH:mm:ss; en pantalla alcanzan las horas y los minutos
export function horaCorta(hora: string): string {
	return hora.slice(0, 5);
}
