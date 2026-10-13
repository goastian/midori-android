# Arranque y pestañas: Firefox aplicado a Midori Android

Fecha: 10 de octubre de 2026. Base de Midori: `8566d65`, con los cambios locales preexistentes de la portada. Comparación de `originalPlaystoreDebug`, versión 11.9.3.2, código 2026000307, en Huawei MIA-LX9 con Android 12. No se realizó ningún commit. Las muestras completas y los SHA-256 de ambos APK están en [resultados-rendimiento-arranque-pestanas.json](resultados-rendimiento-arranque-pestanas.json).

## Fuente y decisiones

Se inspeccionó Firefox Android de la rama `release`, fijada en `3c71aeacdb296402e41fb2084b13e797d1770642`, del 7 de octubre de 2026. La exploración de identificadores y llamadas utilizó `searchfox-cli` con `mozilla-release`; las decisiones se contrastaron con los archivos de ese commit.

- [FenixApplication](https://github.com/mozilla-firefox/firefox/blob/3c71aeacdb296402e41fb2084b13e797d1770642/mobile/android/fenix/app/src/main/java/org/mozilla/fenix/FenixApplication.kt) utiliza una cola para ejecutar tareas opcionales después de la completitud visual. Midori ya separaba el mantenimiento; ahora también retrasa el arranque de traducciones hasta tener la sesión restaurada, un documento pintado y la carga inicial terminada, con el timeout existente de 10 segundos.
- [Core](https://github.com/mozilla-firefox/firefox/blob/3c71aeacdb296402e41fb2084b13e797d1770642/mobile/android/fenix/app/src/main/java/org/mozilla/fenix/components/Core.kt) desactiva la inicialización automática de traducciones y el middleware genérico de suspensión por memoria. Este último tiene antecedentes de problemas de restauración; Firefox confía en GeckoView y Android para recuperar memoria. Midori desactiva ese mismo middleware y añade una política propia con comprobación de formularios y actualización del estado nativo antes de cerrar un motor.
- [SessionPrioritizationMiddleware](https://github.com/mozilla-firefox/firefox/blob/3c71aeacdb296402e41fb2084b13e797d1770642/mobile/android/android-components/components/browser/state/src/main/java/mozilla/components/browser/state/engine/middleware/SessionPrioritizationMiddleware.kt) da prioridad alta a la pestaña seleccionada y conserva prioridad para formularios. Midori aplica ese patrón junto con protección de multimedia, captura y permisos, para respetar la reproducción en segundo plano existente.

El límite de motores descrito aquí es una decisión propia de Midori, no una configuración copiada de Firefox. Se conserva la precarga temprana de Gecko: la comparación anterior del Huawei mostró que ayudaba al contenido inicial. El bloqueador y los ajustes esenciales de privacidad siguen instalándose antes de restaurar y cargar la primera pestaña. No se incorpora telemetría.

## Cambios

Los proveedores de sugerencias y Datahub se obtienen al utilizar la barra, sin construir toda esa dependencia al mostrar el navegador. Marcadores, acciones de PWA y el caso de uso de limpieza también se resuelven bajo demanda; la consulta del historial se construye y ejecuta en IO. La limpieza consulta las preferencias vigentes en cada ejecución, incluida la primera llamada.

La animación de Zap no crea transiciones infinitas ni recursos gráficos mientras está inactiva. Reader View se crea para documentos web, modo lectura activo o su página interna `readerview.html`, incluida la restauración; se difiere en la portada y en documentos internos que no lo requieren. La tira de pestañas deja de observar sus listas cuando está desactivada. Los estados iniciales de URL, restauración, selección y contador proceden del BrowserStore actual.

`TabResourceMiddleware` coordina prioridades y suspensión:

- Objetivo de cinco motores residentes en primer plano y dos en segundo plano o bajo presión de memoria. La Activity comunica `ResumeAction` y `PauseAction` al store.
- Se consideran primero los motores de las pestañas menos utilizadas. Se conservan todas las pestañas, sus IDs y su estado serializado; suspender no equivale a cerrar una pestaña.
- Se excluyen la seleccionada, las que están cargando, formularios modificados, picture-in-picture, sesiones multimedia activas o pausadas, dispositivos de captura y solicitudes pendientes de permisos o prompts. Los custom tabs quedan fuera de esta política.
- Al cambiar de pestaña, se comprueban los formularios de la anterior. Su prioridad permanece alta durante esa comprobación y durante el guardado del estado.
- Antes de suspender una candidata se espera el resultado nativo de formularios y una actualización de `EngineSessionState` solicitada mediante `flushSessionState()`. Si Gecko no responde, el motor se conserva.
- Android Components cierra el motor y lo reconstruye desde su estado al volver a seleccionar la pestaña. No se añade ninguna URL a cerradas recientemente.

El presupuesto es flexible: puede excederse para proteger trabajo del usuario. La agrupación visual de pestañas inactivas a los 14 días continúa siendo independiente de la suspensión de motores.

## Arranque medido

Tres aperturas frías por APK, misma aplicación Debug, mismo perfil con dos pestañas y Midori Tab seleccionada, pantalla encendida y `MainActivity` enfocada. Los recursos de `assets/` y las librerías de `lib/` de los dos APK coinciden por nombres y CRC; ambos usan el mismo certificado Debug. Se realizó una apertura de preparación después de instalar cada versión. Las huellas de los IDs y de la selección coinciden antes y después. Se mantuvo la pantalla encendida por USB durante esta tanda y se restauró su ajuste al terminar.

| Medición, ms | Antes: mediana (rango) | Después: mediana (rango) |
| --- | --- | --- |
| Android `am start -W`, `TotalTime` | 1461 (1453–1525) | 1426 (1418–1432) |
| Callback local del primer frame | 1404 (1383–1455) | 1355 (1339–1362) |
| Restauración completa, primer contenido pintado y carga terminada | 2245 (2198–2303) | 2040 (2038–2118) |
| Aplicación inicializada | 524 (500–546) | 524 (522–526) |

En esta muestra, el contenido inicial llega 205 ms antes, un 9,1 %. La diferencia del callback del primer frame es de 49 ms, un 3,5 %; Android `TotalTime` disminuye 35 ms, un 2,4 %. El primer frame todavía ronda 1,3–1,4 segundos: la demora completa de Debug no está resuelta. Los relojes de Android y del callback local tienen orígenes diferentes y no se restan entre sí.

`initial_content_loaded` no equivale a que todos los widgets e imágenes de la portada estén listos para interactuar. Tres muestras por versión tampoco permiten garantizar esas diferencias en otros dispositivos o en Release. La temperatura de batería registrada pasó de 39 °C antes de la comparación a 40 °C al finalizar las pruebas.

El retorno final desde Home informó `LaunchState: UNKNOWN (0)` y no proporcionó `TotalTime`; se excluye como medición de arranque caliente.

## Memoria y pestañas

Prueba con el mismo APK de instrumentación: dos pestañas originales y 9/49 documentos privados locales de prueba, con un canvas de 1200 × 1200. Se esperan títulos y fin de carga, y tres segundos de estabilización. La prueba elimina sus propios documentos y restaura la selección original.

| Pestañas | Motores antes | Motores después | PSS principal antes, KiB | PSS principal después, KiB |
| --- | --- | --- | --- | --- |
| 2 | 1 | 1 | 559822 | 573079 |
| 11 | 10 | 5 | 622130 | 699745 |
| 51 | 50 | 5 | 699478 | 596607 |

En esta tanda la referencia mantuvo 50 motores con 51 pestañas y la versión nueva mantuvo cinco. La PSS principal del escenario de 51 pestañas bajó 102871 KiB, aproximadamente 100 MiB; en el escenario de 11 pestañas aumentó. Una tanda anterior de la referencia sí había reducido los motores a cinco bajo las condiciones de memoria de ese momento. La nueva política proporciona un presupuesto explícito y guardas de estado sin esperar esa presión del sistema; estas muestras no demuestran una reducción consistente de PSS para todos los escenarios. Las cifras corresponden al proceso principal con instrumentación, no a la suma de los procesos de contenido y GPU de Gecko. La asignación de memoria, el GC y el fondo dinámico de la portada introducen variación.

El APK final pasó la conservación de 51 pestañas con cinco motores y el presupuesto de dos en segundo plano. Una suspendida recuperó un motor y produjo una pintura nueva en 205 ms. Después se añadió una segunda página a su historial, se volvió a suspender y se comprobó una nueva pintura y navegación Atrás a la primera página. Las pestañas originales y la selección se conservaron al limpiar las pestañas de prueba.

## Reproducción y alcance

Para medir un arranque se puede ejecutar:

```sh
python3 scripts/measure-startup.py --serial SERIAL_DEL_HUAWEI \
  --output /tmp/midori-startup-results.json
```

El script no borra datos: detiene el proceso antes de cada apertura, captura únicamente la etiqueta local `MidoriStartup`, filtra el PID, exige una apertura `COLD` con contenido inicial y comprueba el foco de la Activity. Restituye la propiedad temporal de esa etiqueta al terminar.

Para repetir las pruebas de pestañas, compilar e instalar el APK de instrumentación y ejecutar:

```sh
adb -s SERIAL_DEL_HUAWEI shell am instrument -w -r \
  -e class org.midorinext.android.BrowserResourceDeviceTest \
  -e residentLimit 5 \
  org.midorinext.android.debug.test/androidx.test.runner.AndroidJUnitRunner
adb -s SERIAL_DEL_HUAWEI shell run-as org.midorinext.android.debug \
  cat cache/browser-resource-results.json
```

Usar `residentLimit 0` solamente para la versión de referencia: recoge la misma base y no exige la nueva política. La prueba presupone al menos una pestaña restaurada. El total registrado incluye las pestañas originales y las de prueba; con un perfil de una pestaña los escenarios serían 1/10/50, y en esta tanda fueron 2/11/51. Las protecciones de formularios, multimedia y captura se cubren además con pruebas JVM; esta sesión no mide llamadas de videoconferencia ni reproducción prolongada.

Pasan 108 pruebas JVM, `verifyLocalizedStrings`, los APK de Debug y de instrumentación y el empaquetado completo de Release, incluidos R8 y lintVital. La prueba final de dispositivo pasa en 25,247 segundos; la misma prueba para la referencia pasa en 24,486 segundos. Los APK universales de Debug y Release pasan `verify-no-glean.py`; el Debug instalado contiene 21 DEX sin tipos o actividades de Glean. Esta comprobación no cubre telemetría nativa dentro de GeckoView.

P07 sigue pendiente en el backlog: faltan la comparación Release con un perfil equivalente y las mediciones de menú, bandeja y recreación de Activity. Estas mejoras de arranque y gestión de motores no completan por sí solas todo ese protocolo.
