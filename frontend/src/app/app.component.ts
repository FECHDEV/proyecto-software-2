import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import {
	LucideHouse,
	LucideMoon,
	LucidePackage,
	LucideSearch,
	LucideShoppingCart,
	LucideSun,
	LucideUser,
} from '@lucide/angular';

import { CarritoService } from './core/api/carrito.service';
import { MARCA } from './core/marca';
import { nombreCompleto, Rol } from './core/modelos/usuario';
import { SesionService } from './core/sesion/sesion.service';
import { TemaService } from './core/tema/tema.service';

// Los módulos del encabezado, además de Inicio (la tienda) y Catálogo, que los
// ven todos. Ocultarlos según el rol es usabilidad: quien autoriza es el backend
const MODULOS: readonly { ruta: string; nombre: string; roles: readonly Rol[] }[] = [
  { ruta: '/panel', nombre: 'Panel', roles: ['ADMINISTRADOR', 'EMPLEADO', 'CONTADOR'] },
  { ruta: '/usuarios', nombre: 'Usuarios', roles: ['ADMINISTRADOR'] },
  { ruta: '/categorias', nombre: 'Categorías', roles: ['ADMINISTRADOR'] },
  { ruta: '/productos', nombre: 'Productos', roles: ['ADMINISTRADOR'] },
  { ruta: '/inventario', nombre: 'Inventario', roles: ['ADMINISTRADOR', 'EMPLEADO', 'CONTADOR'] },
];

@Component({
  selector: 'app-root',
  imports: [
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
    LucideHouse,
    LucideMoon,
    LucideSearch,
    LucidePackage,
    LucideShoppingCart,
    LucideSun,
    LucideUser,
  ],
  templateUrl: './app.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './app.component.scss',
})
export class AppComponent {
  private readonly sesion = inject(SesionService);
  private readonly router = inject(Router);
  private readonly temas = inject(TemaService);

  protected readonly marca = MARCA;
  protected readonly autenticado = this.sesion.autenticado;
  protected readonly modulos = computed(() => {
    const rol = this.sesion.rol();
    return rol === null ? [] : MODULOS.filter((modulo) => modulo.roles.includes(rol));
  });
  protected readonly nombre = computed(() => {
    const usuario = this.sesion.usuario();
    return usuario === null ? '' : nombreCompleto(usuario);
  });

  // El carrito, solo para el Cliente (HU-11 RF-7), con cuántos productos tiene
  private readonly carrito = inject(CarritoService);
  protected readonly esCliente = computed(() => this.sesion.rol() === 'CLIENTE');
  protected readonly lineasEnElCarrito = this.carrito.cantidadLineas;
  protected readonly etiquetaDelCarrito = computed(() => {
    const lineas = this.lineasEnElCarrito();
    if (lineas === null) {
      return 'Carrito';
    }
    return lineas === 0 ? 'Carrito, sin productos' : `Carrito, ${lineas} ${lineas === 1 ? 'producto' : 'productos'}`;
  });

  // El botón ofrece el otro modo: con el oscuro activo, el sol lleva al claro
  protected readonly enClaro = computed(() => this.temas.tema() === 'claro');

  // La búsqueda del encabezado, en todas las pantallas (HU-09 RF-17): lleva al
  // catálogo con lo escrito
  protected readonly busqueda = signal('');
  // El catálogo tiene su propia búsqueda, que sigue a la dirección: dos a la vez confunden
  private readonly url = toSignal(
    this.router.events.pipe(
      filter((evento) => evento instanceof NavigationEnd),
      map(() => this.router.url),
    ),
    { initialValue: this.router.url },
  );
  protected readonly enCatalogo = computed(() => this.url().split('?')[0] === '/catalogo');

  protected buscar(): void {
    const q = this.busqueda().trim();
    void this.router.navigate(['/catalogo'], { queryParams: q === '' ? {} : { q } });
  }

  constructor() {
    // Lo que queda fijo debajo del encabezado (el letrero de la portada) necesita
    // saber cuánto mide: cambia con el ancho y no ocupa nada cuando no es fijo
    const anfitrion = inject(ElementRef<HTMLElement>);
    const destruccion = inject(DestroyRef);
    afterNextRender(() => {
      const cabecera = (anfitrion.nativeElement as HTMLElement).querySelector('header');
      if (cabecera === null || typeof ResizeObserver === 'undefined') {
        return;
      }
      const medir = () => {
        const fija = getComputedStyle(cabecera).position === 'sticky';
        document.documentElement.style.setProperty(
          '--app-alto-cabecera',
          `${fija ? cabecera.getBoundingClientRect().height : 0}px`,
        );
      };
      const observador = new ResizeObserver(medir);
      observador.observe(cabecera);
      destruccion.onDestroy(() => observador.disconnect());
    });
  }

  protected alternarTema(): void {
    this.temas.alternar();
  }

  protected salir(): void {
    this.sesion.cerrar();
    void this.router.navigate(['/inicio-sesion']);
  }
}
