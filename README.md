# DJ SEBAS EDICIÓN CATCURTIS

Editor de video Android independiente, con interfaz oscura y herramientas locales de montaje.

## Estado actual

Versión de código: **1.24.0**  
Paquete Android: `com.djsebas.catcurtis`  
SDK mínimo: Android 8.0 (API 26)  
Compilación: GitHub Actions genera un APK de depuración.

## Funciones implementadas en el código

- Importación múltiple de videos, fotografías y pistas de audio mediante el selector de archivos de Android.
- Permisos persistentes de lectura para los medios importados.
- Vista previa de video con ExoPlayer y vista previa de fotografías.
- Miniaturas de los medios visuales.
- Línea de tiempo/lista de medios con selección, reordenamiento y eliminación.
- Recorte de inicio y fin de cada video mediante controles de rango.
- Duración ajustable de fotografías (1 a 15 segundos).
- Transición de fundido cruzado de hasta 0.5 segundos durante la exportación.
- Filtros de video: original, blanco y negro y negativo.
- Título superpuesto con texto editable, cinco colores, tamaño ajustable (50 % a 200 %) y posición vertical.
- Historial Deshacer/Rehacer de hasta 30 estados para los ajustes admitidos.
- Mezcla de varias pistas de audio en bucle, con volumen general y volumen individual.
- Control de volumen independiente para la vista previa.
- Exportación MP4 a `Películas/DJ SEBAS CATCURTIS`.

## Pendiente de validación o desarrollo

- Pruebas completas de exportación con combinaciones reales de videos, fotos y varias pistas de audio.
- Verificación en un teléfono físico Android, incluyendo el Moto G24 del usuario.
- Persistencia y recuperación de proyectos/drafts después de cerrar la aplicación.
- Fotogramas clave, animación avanzada, subtítulos automáticos, control de velocidad y biblioteca ampliada de efectos/transiciones.
- Verificar en dispositivo el rendimiento, compatibilidad de códecs y estabilidad con archivos largos.

## Compilar

Con Android Studio o mediante GitHub Actions, ejecutar:

```bash
gradle assembleDebug --no-daemon
```

El APK de depuración se publica como artefacto del flujo de trabajo **Android APK**.

**Importante:** una compilación exitosa confirma que el código compila; no sustituye las pruebas de reproducción y exportación en un dispositivo real.
