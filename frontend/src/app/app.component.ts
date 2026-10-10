import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { LucideHouse, LucideMoon, LucideSun, LucideUser } from '@lucide/angular';

import { MARCA } from './core/marca';
import { nombreCompleto, Rol } from './core/modelos/usuario';
import { SesionService } from './core/sesion/sesion.service';
import { TemaService } from './core/tema/tema.service';

// Los módulos del encabezado, además de Inicio (la portada), que lo ven todos.
// Ocultarlos según el rol es usabilidad: quien autoriza es el backend
const MODULOS: readonly { ruta: string; nombre: string; roles: readonly Rol[] }[] = [
  { ruta: '/usuarios', nombre: 'Usuarios', roles: ['ADMINISTRADOR'] },
  { ruta: '/categorias', nombre: 'Categorías', roles: ['ADMINISTRADOR'] },
];

@Component({
  selector: 'app-root',
  imports: [RouterLink, RouterLinkActive, RouterOutlet, LucideHouse, LucideMoon, LucideSun, LucideUser],
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

  // El botón ofrece el otro modo: con el oscuro activo, el sol lleva al claro
  protected readonly enClaro = computed(() => this.temas.tema() === 'claro');

  protected alternarTema(): void {
    this.temas.alternar();
  }

  protected salir(): void {
    this.sesion.cerrar();
    void this.router.navigate(['/inicio-sesion']);
  }
}
