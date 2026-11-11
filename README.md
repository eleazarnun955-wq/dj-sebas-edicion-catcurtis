# DJ SEBAS EDICIÓN CATCURTIS

Editor de video Android independiente, con interfaz oscura y herramientas locales de montaje.

## Estado actual

Versión de código: **1.32.0**  
Paquete Android de esta edición independiente: `com.djsebas.catcurtis.v131`  
La aplicación conserva su identificador `v129` para que las actualizaciones de esta misma edición se instalen sobre ella sin sustituir otras ediciones con identificadores distintos.  
SDK mínimo: Android 8.0 (API 26)  
Compilación: GitHub Actions genera y publica un APK de depuración; la compilación no sustituye las pruebas funcionales en un dispositivo real.

## Funciones implementadas en el código

- Importación múltiple de videos, fotografías y pistas de audio mediante el selector de archivos de Android.
- Permisos persistentes de lectura para los medios importados.
- Vista previa de video con ExoPlayer y vista previa de fotografías.
- Vista previa del título superpuesto con color, tamaño y posición seleccionados.
- Guardado automático y restauración del último proyecto local, sujeto a que Android conserve los permisos de los archivos importados.
- Al eliminar un medio, la selección pasa al siguiente video o fotografía disponible y se limpian sus ajustes de audio/recorte asociados.
- Confirmación del rango de recorte sin crear entradas duplicadas en el historial Deshacer/Rehacer.
- Miniaturas de los medios visuales.
- Línea de tiempo visual con carriles separados para video/fotografías y audio, bloques seleccionables y duración/volumen visibles.
- Lista de medios con selección, reordenamiento y eliminación.
- Recorte de inicio y fin de cada video mediante controles de rango, con acción explícita para aplicar el corte.
- Duración ajustable de fotografías (1 a 15 segundos).
- Transición de fundido cruzado ajustable de corte directo a 2 segundos durante la exportación.
- Filtros de foto y video por clip: original, blanco y negro, negativo, sepia, cálido y frío; se guardan con el proyecto y se aplican también a la exportación.
- Título superpuesto con texto editable, cinco colores, tamaño ajustable (50 % a 200 %) y posición vertical.
- Historial Deshacer/Rehacer de hasta 30 estados para los ajustes admitidos.
- Mezcla de varias pistas de audio en bucle, con volumen general y volumen individual.
- Control de volumen independiente para la vista previa.
- Exportación MP4 a `Películas/DJ SEBAS CATCURTIS`.

## Pendiente de validación o desarrollo

- Pruebas completas de exportación con combinaciones reales de videos, fotos y varias pistas de audio.
- Verificación en un teléfono físico Android, incluyendo el Moto G24 del usuario.
- Administración de varios proyectos independientes con nombres y guardado manual (actualmente se conserva automáticamente el último proyecto).
- Fotogramas clave, animación avanzada, subtítulos automáticos, control de velocidad y biblioteca ampliada de efectos/transiciones.
- Verificar en dispositivo el rendimiento, compatibilidad de códecs y estabilidad con archivos largos.

## Compilar

Con Android Studio o mediante GitHub Actions, ejecutar:

```bash
gradle assembleDebug --no-daemon
```

El APK de depuración se publica como artefacto del flujo de trabajo **Android APK**.

**Importante:** una compilación exitosa confirma que el código compila; no sustituye las pruebas de reproducción y exportación en un dispositivo real.
