# DJ SEBAS EDICIÓN CATCURTIS

Editor de video Android independiente, con herramientas locales de montaje.

## Versión actual

- Versión: **1.39.0**
- Paquete Android independiente: `com.djsebas.catcurtis.v139`
- SDK mínimo: Android 8.0 (API 26)
- GitHub Actions genera el APK; la compilación no sustituye las pruebas funcionales en un teléfono físico.

## Funciones implementadas en el código

- Importación múltiple de videos, fotografías y pistas de audio.
- Permisos persistentes de lectura para medios importados.
- Vista previa de video con ExoPlayer y vista previa animada de fotografías.
- Movimiento de fotografías Ken Burns: zoom de acercamiento/alejamiento, paneos horizontales/verticales y **zoom combinado con paneo**.
- Los movimientos de fotografía se aplican en la vista previa y durante la exportación MP4.
- Filtros por clip: original, blanco y negro, negativo, sepia, cálido y frío.
- Transiciones de fundido ajustables por clip.
- Línea de tiempo visual con carriles de video/fotografía y audio; selección y reordenamiento de clips.
- Recorte de inicio y fin de video y audio.
- Duración ajustable de fotografías y ajuste automático aproximado a la duración de la música.
- Título superpuesto con texto, color, tamaño y posición configurables.
- Varias pistas de audio con volumen general, volumen individual y recorte independiente.
- Guardado automático/restauración del último proyecto local.
- Historial Deshacer/Rehacer para los ajustes admitidos.
- Exportación MP4 a `Películas/DJ SEBAS CATCURTIS`.

## Pendiente de validación o desarrollo

- Pruebas completas en dispositivo físico de reproducción y exportación con distintos códecs y combinaciones de medios.
- Fotogramas clave, animación avanzada, subtítulos automáticos, control de velocidad y más transiciones.
- Administración de varios proyectos con nombres y guardado manual.
- Integración directa con Ani/Anijam: no se ha confirmado una API pública documentada. Mientras tanto, se pueden importar escenas o videos generados externamente. El movimiento Ken Burns local no equivale a generación de escenas por IA.

## Compilar

```bash
gradle assembleDebug --no-daemon
```

El APK de depuración se publica como artefacto del flujo **Android APK**.
