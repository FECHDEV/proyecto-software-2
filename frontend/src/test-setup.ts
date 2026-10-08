// Preparación común de las pruebas (angular.json → test → setupFiles).
//
// jsdom no implementa showModal() ni close() de <dialog>. Este reemplazo
// mínimo reproduce lo que usan las pantallas: abrir marca `open`, y cerrar
// quita `open`, guarda el valor devuelto y emite el evento `close`, que es lo
// mismo que hace el navegador también al cerrar con Escape.
if (typeof HTMLDialogElement !== 'undefined' && !HTMLDialogElement.prototype.showModal) {
  HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
    this.setAttribute('open', '');
  };
  HTMLDialogElement.prototype.close = function (this: HTMLDialogElement, valor?: string) {
    if (!this.hasAttribute('open')) {
      return;
    }
    this.removeAttribute('open');
    if (valor !== undefined) {
      this.returnValue = valor;
    }
    this.dispatchEvent(new Event('close'));
  };
}
