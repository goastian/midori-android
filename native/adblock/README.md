# Motor nativo de Midori Android

El adaptador JNI usa `brave/adblock-rust` 0.13.3, revisión
`6028a6d5735f1e81346eeef73c194c7240035248`, fijada también en `Cargo.lock`.
La biblioteca Rust es independiente de Chromium. GeckoView aplica sus decisiones
mediante la extensión interna de Midori.

Se necesita Python 3, Rust 1.98.0 y Android NDK 29.0.14206865. Preparación:

```sh
rustup toolchain install 1.98.0 --profile minimal
rustup target add --toolchain 1.98.0 aarch64-linux-android armv7-linux-androideabi x86_64-linux-android i686-linux-android
```

Gradle compila y empaqueta la biblioteca automáticamente para las ABI del proyecto.
`-PfullAbiBuild=true` incluye también x86. La biblioteca Linux de las pruebas JVM
se construye con `buildHostAdblock`. El enlace Android conserva alineación de
16 KiB. No se incluyen bibliotecas precompiladas externas en el repositorio.

```sh
cargo +1.98.0 test --locked --manifest-path native/adblock/Cargo.toml
./gradlew :app:generateBundledFilters
./gradlew :app:testOriginalPlaystoreDebugUnitTest --tests 'org.midorinext.android.adblock.*'
python3 scripts/vendor-adblock-licenses.py
```

Las listas se preprocesan para las capacidades de Gecko y se reúnen antes de
compilar dos motores: rastreadores y protección completa. El motor usa su propia
resolución de dominios y sufijos privados. Las excepciones explícitas `$document`
se compilan también por separado para permitir la página y sus marcos sin
convertir una excepción de un script en una excepción de toda la página.

Los identificadores JNI se guardan en un registro sincronizado con referencias
`Arc`; no se convierten punteros sin validar a enteros. La sustitución de los
motores conserva consultas concurrentes y libera los motores anteriores.
Los errores de JNI se convierten en excepciones Java y el puente conserva la
carga de la página si falla una consulta.

La serialización combina las dos representaciones de adblock-rust con una longitud
de 64 bits validada. Las cachés se identifican por la revisión del motor, la versión
del adaptador, las listas preprocesadas y los recursos incluidos. Los respaldos
binarios del APK se generan con el mismo adaptador y se verifican en GeckoView.

Los scriptlets continúan usando los catálogos fijados de uBlock y AdGuard. Rust
procesa red y selectores; no ejecuta código obtenido de las suscripciones.
Las licencias del motor y de sus dependencias están incluidas en
`app/src/main/assets/adblock/rust-licenses`.
