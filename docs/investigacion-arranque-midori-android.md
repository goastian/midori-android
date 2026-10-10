# Investigación del arranque de Midori Android

Fecha inicial: 9 de octubre de 2026; medición en Huawei: 10 de octubre de 2026. Base inicial: `5c0d5d7`, versión `11.9.3.2`. El reporte indica demora al reabrir y al iniciar tras cerrar completamente la app. El video recibido posteriormente permite observar la espera, pero no establece una comparación controlada de rendimiento.

Se revisaron `MidoriApplication`, `MainActivity`, la inicialización de Gecko, la restauración de sesión y el bloqueador nativo. La colección de cerradas recientemente de P05 solo registra pestañas durante los cierres y no se utiliza para restaurar la sesión al arrancar.

## Cambios aplicados

- La actualización de fechas de listas de filtros emitía un nuevo `AdBlockConfiguration` y volvía a aplicar toda la configuración de Gecko, incluidos DNS y privacidad. `geckoSettingsChanges` proyecta los ajustes efectivos y elimina emisiones iguales. Los cambios reales de protección, DNS y privacidad siguen llegando a Gecko; autocompletado y protección del sistema observan las preferencias de la app por separado.
- La programación de `AdBlockUpdateWorker` se ejecutaba en el hilo de interfaz al recibir el callback del primer frame. Ahora se ejecuta en `Dispatchers.IO`, junto con el mantenimiento ya diferido.
- El puente de red esperaba la política de filtros hasta 2.000 ms antes de comprobar si la página pertenecía a un esquema interno. Las solicitudes HTTP de recursos de una página `moz-extension`, `about` o `file` podían quedar esperando aunque el resultado posterior fuese permitirlas. Ahora se comprueba primero el esquema de la página. Las páginas HTTP/HTTPS, incluidas las privadas, mantienen las comprobaciones nativas existentes.
- La extensión interna de protección pasa a `1.2`, para que las instalaciones existentes incorporen el cambio de su script.

## Comprobaciones

Con `playstore_version_code=2026000302` fijado:

```sh
./gradlew --offline --console=plain -Pplaystore_version_code=2026000302 \
  :app:testOriginalPlaystoreDebugUnitTest \
  :app:compileOriginalPlaystoreReleaseKotlin \
  :app:verifyLocalizedStrings \
  :app:assembleOriginalPlaystoreDebug
./gradlew --offline --console=plain -Pplaystore_version_code=2026000302 \
  :app:assembleOriginalPlaystoreRelease
node scripts/tests/native-network-blocking.test.mjs
```

Pasan las 91 pruebas JVM de Debug, incluidas cuatro nuevas de los ajustes de Gecko, y las ocho pruebas del puente JavaScript. Pasan la compilación Kotlin de Release, la comprobación de localización y el empaquetado Debug y Release, incluido `lintVital` y R8. El entorno JavaScript usa el script real con un navegador simulado; estas pruebas no miden el tiempo completo del navegador.

En la comprobación inicial, el emulador conectado era Android 14, con ABI `x86_64` y compatibilidad `arm64-v8a`. La instalación del APK de referencia falló con `INSTALL_FAILED_INSUFFICIENT_STORAGE`. Un ajuste temporal del umbral de espacio no permitió instalarlo y se restauró al valor original. No se borraron datos de aplicaciones. Los registros de las comprobaciones están en `/tmp/midori-startup-fix-validation.log` y `/tmp/midori-startup-release-build.log`.

## Video de 11.9.3.2 y 11.9.3

Archivo: `video_2026-10-09_23-03-56.mp4`, duración 10,589 segundos. El usuario identifica la primera aplicación como 11.9.3.2 y la segunda como 11.9.3; confirma que **11.9.3.2 es Debug**. Las marcas siguientes proceden de los timestamps de presentación de los fotogramas originales, porque la grabación tiene frecuencia variable. Dividir el número de fotograma por su frecuencia nominal no proporciona tiempos correctos.

| Hito visible | 11.9.3.2 | 11.9.3 |
| --- | --- | --- |
| Inicio de la animación de apertura | 3,180 s | 8,852 s |
| Primera barra visible, todavía arriba y sin sesión restaurada | 4,521 s | 9,149 s |
| Barra abajo con la sesión visible | 4,703 s | 9,246 s |
| Portada de Midori Tab visible con estructura y texto | 5,449 s | No aparece antes del final del video |

La primera barra tarda aproximadamente 1,34 s frente a 0,30 s desde el inicio de cada animación. La portada de la primera aparece a los 2,27 s; algunos iconos e imágenes siguen llegando después. Son observaciones de una grabación, no mediciones del proceso Android ni pruebas de interacción. No se puede determinar si ambos procesos estaban detenidos. Se ven perfiles distintos: una pestaña en la primera y 19 en la segunda; la segunda restaura una página web, no la portada.

En el proyecto, Debug activa registros de Rust a nivel DEBUG, `consoleOutput`, `debugLogging`, `remoteDebuggingEnabled` y `testingModeEnabled` de Gecko. Release usa INFO y desactiva esas opciones; además aplica R8 y reducción de recursos. Debe compararse Release con Release antes de atribuir toda la diferencia al nuevo código. La retirada del SDK Android de Glean no demuestra por sí sola una mejora del arranque.

## Ajustes posteriores al video

- El delegado de autocompletado recibía directamente `SyncableLoginsStorage` y `AutofillCreditCardsAddressesStorage`. El constructor de `SyncableLoginsStorage` lanza inmediatamente una coroutine que obtiene la clave y abre la base de datos. Hilt también resolvía las preferencias cifradas antes de construir ambos almacenes. Ahora las dependencias permanecen diferidas hasta que una operación habilitada necesita almacenamiento. Los mismos almacenes, claves y comprobaciones de preferencias se conservan.
- La instalación de la extensión opcional VPN y el mantenimiento esperan a la restauración de sesión y a que la pestaña seleccionada haya pintado contenido y terminado de cargar. El placeholder de la portada no satisface esa condición. El límite de diez segundos permite ejecutar el mantenimiento si no hay documento, hay onboarding o falla la carga. Los observadores de medios siguen empezando después del primer frame; las preferencias de protección y el bloqueador siguen iniciándose antes de restaurar pestañas.
- El primer ajuste también pospuso `warmUp()` hasta después del documento, lo que impedía que ayudase a esa carga. La inspección del GeckoView utilizado confirma que precarga procesos de contenido; no es mantenimiento de sesiones. Se corrigió para llamar a `GeckoRuntime.warmUp()` en el proceso principal, inmediatamente después de los ajustes iniciales, con una etapa local `gecko_warm_up`. La [documentación de GeckoRuntime](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/GeckoRuntime.html#warmUp()) describe su propósito de acelerar la primera carga.
- `StartupTiming` registra etapas con el tag local `MidoriStartup` y secciones de Trace `Midori.*`. Incluye versión y tipo de build, inicialización de App Services, creación de Gecko, ajustes iniciales, motor, puente nativo y primer frame. `initial_content_loaded` marca el cumplimiento de la condición anterior; `maintenance_timeout` indica el límite de espera, no una carga satisfactoria. No registra URLs, pestañas, cuentas ni eventos de uso, y no envía datos. Los hitos temporales se expresan desde el inicio del proceso; las etapas `took` indican duración individual.

Pasan 101 pruebas JVM de la aplicación: seis nuevas verifican la espera de contenido, carga, restauración, placeholder, documento privado y timeout; cuatro verifican que el delegado no abre almacenes deshabilitados, conserva el guardado y supervisa fallos al inicializar almacenamiento. Pasan también `compileOriginalPlaystoreReleaseKotlin`, `verifyLocalizedStrings`, `verifyNoGlean` y la generación de los APK Debug y Release con código 2026000303, incluidos R8 y `lintVital`. Los ocho APK de ambas variantes pasan `verify-no-glean.py`; esta comprobación cubre DEX y manifiestos, no elimina el Glean nativo de GeckoView. Se comprobó que R8 conserva el tag `MidoriStartup` y la identificación de la versión Release en su DEX. Registros: `/tmp/midori-startup-video-validation.log`, `/tmp/midori-startup-video-release.log` y `/tmp/midori-startup-video-artifacts.log`.

APK Release ARM64 para la comparación: `app/build/outputs/apk/originalPlaystore/release/app-original-playstore-arm64-v8a-release.apk` (11.9.3.2, código 2026000303).

Para recoger las etapas de esta compilación sin mezclar los logs de otros procesos:

```sh
adb shell pidof org.midorinext.android
adb logcat --pid=PID_DEL_NAVEGADOR -v time MidoriStartup:I '*:S'
```

El PID es el del proceso principal del navegador. Leer estos registros no sustituye medir la presentación de frames o la interacción con Macrobenchmark/Perfetto; `first_frame` corresponde al callback publicado después del pre-draw de `MainActivity`. Durante la revisión del video no había un dispositivo conectado; las mediciones posteriores en el Huawei se documentan a continuación.

## Medición en Huawei MIA-LX9

10 de octubre de 2026. Huawei MIA-LX9, Android 12 / API 31, ARM64, conectado por USB y cargando. Debug `org.midorinext.android.debug`, versión 11.9.3.2, código 2026000307. Se guardó el APK instalado como referencia y se comparó con el APK corregido, ambos universales con 21 DEX. Los archivos de la portada, bloqueador y librerías nativas tienen los mismos CRC en ambos ZIP; la precarga cambia en el código Android. Las identidades SHA-256, muestras y secciones principales del perfil están en [mediciones-arranque-huawei-mia-lx9.json](mediciones-arranque-huawei-mia-lx9.json).

La comparación final usa el mismo perfil, una pestaña visible y Midori Tab seleccionada. Incluye tres aperturas frías por APK con el teléfono libre, comprobando el foco de `MainActivity`. Se excluyeron la primera apertura tras instalar, una apertura que restauró ajustes, la ejecución con tracing y la tanda interrumpida por la vista de aplicaciones recientes. Las tres primeras muestras preliminares habían dado aproximadamente 1,39 s de Android y 2,42 s de contenido; no se mezclan con la comparación final. El fondo de la portada cambia dinámicamente. La temperatura de batería observada durante la sesión estuvo entre 37 y 42 °C.

Todas las cifras siguientes están en milisegundos; se muestran mediana y rango de las tres muestras. Los relojes de `am start` y de `StartupTiming` tienen orígenes distintos, por lo que sus valores no deben restarse entre sí.

| Medición | APK de referencia | APK corregido |
| --- | --- | --- |
| Primer frame de Android, `am start -W` / `TotalTime` | 1.419 (1.406–1.432) | 1.433 (1.404–1.473) |
| Callback local `first_frame` | 1.356 (1.355–1.363) | 1.392 (1.365–1.414) |
| Restauración terminada, contenido pintado y carga finalizada | 2.448 (2.427–2.462) | 2.155 (2.091–2.156) |
| `application_ready` | 503 (502–531) | 518 (507–535) |

La precarga adelantada tarda 0–1 ms en el hilo principal. La mediana del documento inicial disminuye 293 ms en esta comparación local; **el primer frame de la interfaz no mejora** y continúa alrededor de 1,4 s. No se considera solucionada la demora completa del navegador. `initial_content_loaded` no mide el momento en que todos los widgets, imágenes remotas o controles de la portada están listos para interactuar.

Tres retornos desde Home al Debug corregido dieron 59, 60 y 61 ms de Android, todos `LaunchState: HOT`, con el mismo PID y `MainActivity` enfocada. Son retornos de la Activity con proceso conservado, sin una nueva carga de página. No deben compararse con las aperturas `COLD`.

Se recogió aparte un `atrace` con `gfx`, `view`, `am`, `wm`, `sched`, `freq` y `dalvik`. El hilo principal espera aproximadamente 248 ms por el classloader mientras otro hilo abre DEX. La primera traversal ocupa aproximadamente 559 ms, incluidos unos 111 ms de inicialización de Compose y recomposiciones de 84 y 225 ms. Son duraciones inclusivas: no se suman las secciones anidadas. Este perfil identifica costes de la carga de clases y del dibujo inicial de Debug; no demuestra una regresión frente a la versión Release 11.9.3 instalada con otro perfil.

Para reproducir una apertura fría con una sola conexión USB:

```sh
adb -d shell am force-stop org.midorinext.android.debug
adb -d shell am start -W -f 0x10008000 \
  -n org.midorinext.android.debug/org.midorinext.android.MainActivity
adb -d shell pidof org.midorinext.android.debug
adb -d shell dumpsys window
```

Mantener la pantalla encendida y el teléfono sin interacción. Iniciar la captura del tag `MidoriStartup` antes de lanzar la app y filtrar después por el PID principal de esa muestra; el buffer del Huawei puede sobrescribir hitos si solo se consulta varios segundos después. Su propiedad global de logs era `persist.log.tag=M`; se habilitó únicamente `log.tag.MidoriStartup=I` durante la medición y se restauró su valor vacío al terminar. No se borraron datos ni se desinstaló la aplicación; quedó instalado el Debug corregido con el mismo código 2026000307. Los registros de la comparación final están en `/tmp/midori-huawei-confirm-before-*` y `/tmp/midori-huawei-confirm-after-*`.

Pasan 101 pruebas JVM, la compilación Kotlin de Release, `verifyLocalizedStrings`, `verifyNoGlean` y la generación de Debug. El APK universal instalado pasa también `verify-no-glean.py` para sus 21 DEX y su manifiesto; esta comprobación no cubre el Glean nativo de GeckoView. Registros de build: `/tmp/midori-huawei-warmup-build.log` y `/tmp/midori-huawei-final-debug-build.log`.

## Medición pendiente

P07 permanece abierta: ya existe una base local de arranque Debug en Huawei con una pestaña, pero faltan Release con perfil equivalente, las rutas de menú y bandeja y los escenarios de 10 y 50 pestañas. Registrar también la recreación de la Activity con proceso conservado. La comparación de tres aperturas no sustituye Macrobenchmark/Perfetto ni verifica la interacción de la portada.
