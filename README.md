# Simulador de Batalla RPG (rpg-base) - Producto final

Simulador de combate por consola en **Java 21** que reune cuatro entregas del programa ADSO en un solo proyecto:

- **Taller de Concurrencia (Unidades 4.1 y 4.2):** temporizador de turno, efectos DoT/HoT en hilos, torneo con semifinales en paralelo y auto-guardado en un hilo daemon.
- **Taller de Persistencia en Archivos:** historial de combates en texto, guardado y carga de partida con serializacion segura.
- **Unidad 5.2 (JDBC + MVC):** registro persistente de heroes en MySQL y conversion a entidades de combate.
- **Base del juego:** el simulador de los talleres 1 a 8 (POO, colecciones, excepciones, genericos).

## Requisitos

- JDK 21
- Maven 3.9+ (opcional si compilas con `javac`)
- MySQL 8.0.16 o posterior

## Como compilar y ejecutar

Con Maven:

```
mvn -q compile
mvn org.codehaus.mojo:exec-maven-plugin:3.5.0:java -Dexec.mainClass=com.rpg.Main
```

Para compilar fuentes sin Maven (desde la carpeta del proyecto):

```
javac -encoding UTF-8 -Xlint:all -d out $(find src/main/java -name "*.java")
java -cp out com.rpg.Main
```

En IntelliJ IDEA basta con abrir la carpeta como proyecto Maven y ejecutar `Main`.

### Suite de pruebas (`DryRunSuite`)

La suite no usa JUnit: es un programa con `main`. Se ejecuta desde la **raiz del proyecto** (necesita encontrar `src/`):

```
javac -encoding UTF-8 -Xlint:all -cp out -d out-test src/test/java/com/rpg/test/DryRunSuite.java
java -cp out:out-test com.rpg.test.DryRunSuite
```

(En Windows usa `;` en lugar de `:` en el classpath.) Debe terminar con `0 fallidas`.

## Configuracion de MySQL

1. Ejecuta `schema_grupal.sql` en MySQL para crear `rpg_grupal_db` y cargar heroes de demostracion.
2. Copia `config.example.txt` como `config.txt` y completa usuario y clave.
3. `config.txt` esta excluido por `.gitignore` y no debe subirse al repositorio.

El `DROP TABLE IF EXISTS heroes` del esquema esta comentado para proteger los datos existentes.

## Menu del registro de heroes

| Opcion | Funcion |
|---|---|
| 1 | Insertar heroe |
| 2 | Listar heroes |
| 3 | Buscar por tipo |
| 4 | Actualizar nivel |
| 5 | Eliminar heroe |
| 6 | Cargar heroe de MySQL y combatir con su tipo, vida maxima y nivel |
| 0 | Salir |

## Estructura de paquetes

```
com.rpg
  Main                      una sola linea: new HeroeUI().iniciar()
  view/                     HeroeUI (Scanner + Controller, sin JDBC)
  controller/               validaciones y conversion de Heroe a Personaje
  dao/                      ConexionDB y HeroeDAO (PreparedStatement)
  model/                    POJO Heroe
  core/                     flujo del juego
    MotorJuego, Arena, TorneoManager, Bestiario, RegistroCombate, FormatoCombate, Accion,
    Consola, EntradaConsola (lectura con limite de tiempo), TurnoCallback
  concurrency/              hilos: TemporizadorTurnoTask, GestorTurno, EfectoContinuoTask, AutoGuardadoDaemon
  persistencia/             GestorPartidas (serializacion con ObjectInputFilter, File.delete / Files.deleteIfExists)
  entities/                 dominio: Personaje (abstracta), Guerrero, Mago, Enemigo (final), EstadoEfecto, Arma,
                            Gremio, Escuadron, PerfilJugador, enums/
  items/                    Inventario<T>, Pocion
  exceptions/               JuegoException y derivadas
  demo/                     DemoHilosDaemon, DemoArchivos (demostraciones del taller)
```

## Documentacion

`INFORME.md` documenta la secuencia MVC de insercion, la proteccion de consultas con
`PreparedStatement` y las correcciones de concurrencia y persistencia en archivos.

## Lo que ya debes tener (checklist base)

| Taller | Concepto | Donde se ve |
|--------|----------|-------------|
| T1 | `switch` moderno (`->`), ternario, condiciones `&&` / `\|\|` | `MotorJuego.pedirAccion`, `Arena.atacar` (ternario del critico), `Arena.turnoAutomatico` |
| T1 | `equals` vs `==` en String; division decimal y casteos | `Bestiario.crearEnemigo` (`equalsIgnoreCase`), `Personaje.defender` y `calcularDanio` |
| T2 | Bucles `while` / `do-while` / `for`, `break` / `continue` | `MotorJuego.combatir`, `turnoJugador` (do-while), `Consola.leerEntero` (reintento con `continue`), `Personaje.procesarEfectos` |
| T3 | `String`: `trim`, `equalsIgnoreCase`, `format`, `StringBuilder` | `Consola.leerNombre`, `FormatoCombate.barraVida`, `RegistroCombate`, `MotorJuego.guardarProgreso` |
| T4 | Clases, objetos, constructores sobrecargados y `this(...)` | `Guerrero`, `Mago`, `Enemigo`, `Pocion`, `Personaje` |
| T5 | Encapsulamiento: atributos privados, getters/setters validados | `Personaje.setVida` (nunca < 0 ni > vida maxima), `setNivel`, `setNombre`, `Arma` |
| T6 | Clase abstracta, interfaz, polimorfismo, genericos | `Personaje`, `ICombatiente`, `realizarAtaqueEspecial` en `Guerrero` / `Mago` / `Enemigo`, `Inventario<T>` |
| T7 | Composicion, agregacion, asociacion | `Personaje` crea su `Inventario` (composicion); `Gremio` y `Escuadron` (agregacion); `Personaje.equiparArma` y `unirseAGremio` (asociacion) |
| T8 | Enums, `Set`, `Map`, excepciones propias | `enums/`, `Personaje.habilidades` (`HashSet`), `PerfilJugador.logros` (`TreeSet`), `Bestiario` (`Map`), `exceptions/` |

## Checklist del taller de concurrencia (completado)

### Antes de empezar

- [x] El proyecto compila y ejecutas `Main` sin errores.
- [x] Jugaste un combate rapido y un torneo completo, y localizaste los comentarios `TALLER CONCURRENCIA`.
- [x] Repasaste la tabla "Lo que ya debes tener".

### Seccion 1: Temporizador de turno (`Thread`, `Runnable`, `sleep`)

- [x] Interfaz `TurnoCallback` con `void onTiempoAgotado()`.
- [x] `TemporizadorTurnoTask` implementa `Runnable` y recibe la duracion en segundos y el `TurnoCallback`.
- [x] En `run()`, cuenta regresiva decreciente, un segundo por iteracion.
- [x] `Thread.sleep(1000)` dentro de un `try-catch` que captura `InterruptedException`.
- [x] Al llegar a 0 se llama a `callback.onTiempoAgotado()`.
- [x] `GestorTurno` crea el `Thread` y llama a `.start()`.
- [x] Integrado en `MotorJuego.pedirAccion`: si el tiempo se agota el turno se pierde y **el juego continua solo** (lectura de teclado con limite de tiempo).
- [x] Si el jugador responde a tiempo, el temporizador se cancela con `.interrupt()` (sin banderas booleanas manuales).

### Seccion 2: Efectos continuos DoT / HoT (`interrupt`, `InterruptedException`)

- [x] `EfectoContinuoTask` (`Runnable`) con el efecto y su intervalo.
- [x] `run()` usa `while (!Thread.currentThread().isInterrupted())` y aplica dano o curacion sobre el `Personaje`.
- [x] Ante `InterruptedException`: restaura la bandera con `Thread.currentThread().interrupt()` y sale con `break`.
- [x] La limpieza esta centralizada en un `finally`: finaliza la copia del efecto y lo retira del `Personaje` (solo si el hilo sigue siendo el registrado, para no borrar un efecto reaplicado). Tambien se ejecuta cuando el objetivo muere.
- [x] Cada efecto corre en su propio hilo; no se usa `Thread.stop()` ni banderas booleanas.
- [x] La logica por turnos de `Personaje.procesarEfectos` ya no duplica los ticks de efectos con hilo activo.
- [x] Al reaplicar, quitar un efecto o terminar el combate, el hilo del efecto se interrumpe y termina limpio.

### Seccion 3: Torneo en paralelo (`join`)

- [x] `TorneoManager.jugarTorneo` crea dos `Thread`, uno por semifinal, cada uno con su `Runnable`.
- [x] Ambos se inician con `.start()` y corren al mismo tiempo (`simularSemifinal` no es `synchronized`).
- [x] El hilo principal hace `.join()` de la semifinal A y luego de la B.
- [x] Los `join()` estan protegidos con `try-catch` de `InterruptedException`. Si el hilo principal es interrumpido, el torneo se **cancela** (se interrumpen y esperan ambas semifinales, se restaura la bandera, se avisa al usuario y se devuelve `null`): nunca se lee un finalista parcial ni se inventa un campeon.
- [x] Los ganadores se consultan solo despues de que ambos `join()` retornan y entonces se juega la final.
- [x] Se imprime `Thread.getState()` de cada semifinal (`NEW`, `RUNNABLE`, `TIMED_WAITING`, `TERMINATED`).

### Seccion 4: Auto-guardado en segundo plano (hilo daemon)

- [x] `AutoGuardadoDaemon` con bucle `while (true)` y `Thread.sleep(intervalo)`; sale con `break` desde el `catch (InterruptedException)`.
- [x] El guardado escribe el perfil a disco (`partidas/perfil_auto.dat`) bajo el mismo cerrojo que usa el hilo principal.
- [x] `setDaemon(true)` se llama **antes** de `.start()`.
- [x] La aplicacion cierra de inmediato al salir, sin esperar al hilo de guardado.

### Seccion 5: Reto integrador

- [x] Los cuatro modulos funcionan juntos: temporizador de turno, efectos DoT/HoT, torneo con `join` y auto-guardado daemon.
- [x] Estructura: `com.rpg.core`, `com.rpg.entities`, `com.rpg.concurrency` y `Main`.
- [x] El combate y el torneo se juegan completos, sin bloqueos ni excepciones sin controlar.

### Seccion 6: Depuracion y condiciones de carrera

- [x] Reprodujiste la condicion de carrera sobre la vida de un `Personaje`.
- [x] La corregiste en `recibirDanio` y `curar` con `synchronized`.
- [x] Protegiste tambien `estado`, `efectos`, `estaVivo()`, `getVidaMaxima()` y `setVidaMaxima()`.
- [x] Puedes explicar por que `AtomicInteger.addAndGet(-danio)` no basta: `setVida` acota la vida a `0..vidaMaxima` y cambia `estado`.
- [x] Ningun `catch (InterruptedException)` esta vacio ni solo imprime la traza.
- [x] Nunca se llama a `.start()` dos veces sobre el mismo `Thread`.
- [x] Nunca se llama a `run()` directamente (salvo en pruebas que lo hacen dentro de un `Thread`).
- [x] Todos los `Thread.sleep()` estan dentro de `try-catch` que gestiona `InterruptedException`.
- [x] Todo hilo de auto-guardado o monitoreo usa `setDaemon(true)` antes de `.start()`.
- [x] El `join()` del torneo espera a ambas semifinales antes de leer la memoria compartida de los ganadores.

## Checklist del taller de archivos (completado)

### Seccion 1: Clase `File`

- [x] `File` con ruta relativa y ruta absoluta (`getAbsolutePath()`), y diferencia explicada (`INFORME.md` seccion 8.1, `DemoArchivos.mostrarRutas`).
- [x] `exists()`, `length()` y `getName()` para mostrar metadatos (`GestorPartidas.mostrarMetadatos`).
- [x] `createNewFile()` con manejo de `IOException` (`DemoArchivos.demostrarCreateNewFile`).
- [x] Carpeta `partidas/` creada con `mkdirs()` sin fallar si ya existe.

### Seccion 2: Escritura (`BufferedWriter` + `FileWriter`)

- [x] Log de `RegistroCombate` volcado a `historial_combates.txt` con `BufferedWriter` + `FileWriter`.
- [x] Escritura con `newLine()`.
- [x] `FileWriter(file, true)` (append): los combates anteriores no se borran.
- [x] El escritor se cierra con `try-with-resources` (flush al cerrar).

### Seccion 3: Lectura (`BufferedReader` + `FileReader`)

- [x] Lectura con `while ((linea = lector.readLine()) != null)`.
- [x] Opcion 6 del menu muestra el historial.
- [x] `FileNotFoundException` capturada con mensaje claro y sin tildes.

### Seccion 4: Borrado

- [x] `File.delete()` (autoguardado) revisando el `boolean`.
- [x] `Files.deleteIfExists(Path)` (partida principal) y diferencia explicada en `INFORME.md`.
- [x] Opcion 7 del menu borra la partida confirmando antes.

### Seccion 5: Serializacion

- [x] Clases del dominio `Serializable`: `Personaje`, `Guerrero`, `Mago`, `Enemigo`, `Inventario`, `Pocion`, `Arma`, `Gremio`, `EstadoEfecto`, `PerfilJugador`.
- [x] `serialVersionUID` en cada clase serializable y su funcion explicada (`INFORME.md` seccion 8.3).
- [x] Campos `transient` (`hilosEfectos`, `vidaLock`) que vuelven con su valor por defecto y se recrean en `readObject`.
- [x] Guardado con `ObjectOutputStream.writeObject(...)` en `.dat`.
- [x] Carga con `ObjectInputStream.readObject()` y `ObjectInputFilter` (`com.rpg.**;java.base/*;!*`).
- [x] `NotSerializableException` provocada y resuelta (`DemoArchivos.demostrarNotSerializableException`, `INFORME.md` seccion 8.4).
- [x] El ciclo `Personaje` - `Gremio` se serializa correctamente.
- [x] Opcion "Cargar partida" en el menu.

### Seccion 6: recursos y excepciones

- [x] Los flujos de archivos se abren con `try-with-resources`.
- [x] `IOException`, `ClassNotFoundException` y `NotSerializableException` con mensajes utiles.
- [x] Ningun `catch` vacio.
- [x] El juego no se cae si el archivo no existe, esta corrupto o no se puede escribir.

### Seccion 7: Integracion con concurrencia

- [x] `AutoGuardadoDaemon` escribe a disco cada 30 s.
- [x] La escritura y la lectura de los `.dat` estan protegidas con `synchronized` (un mismo candado en `GestorPartidas`; el historial `.txt` tiene el suyo).
- [x] Al cerrar el juego el hilo daemon no deja un archivo a medio escribir (la escritura ocurre dentro del cerrojo).

## Investigacion obligatoria

- [x] **Hilos daemon.** `DemoHilosDaemon` demuestra que `setDaemon(true)` despues de `start()` lanza `IllegalThreadStateException` y que la JVM cierra aunque el daemon siga vivo. Un hilo de usuario mantiene viva la JVM; un daemon no.
- [x] **`ReentrantLock`.** `Personaje.recibirDanioConLock` usa `lock()` / `unlock()` dentro de `try-finally`. Se prefiere sobre `synchronized` cuando se necesita `tryLock()`, esperas con tiempo limite, equidad (FIFO) o varias `Condition`.
- [x] **`FileWriter` vs `Files.writeString`.** `FileWriter` + `BufferedWriter` es un flujo con buffer que abres y cierras tu (ideal para logs que crecen); `Files.writeString` es una sola llamada de NIO que escribe todo el texto. `Files.writeString` **no garantiza atomicidad a nivel del sistema operativo**: una falla a mitad de la escritura puede dejar el archivo incompleto (ver `INFORME.md` seccion 8.5).
- [x] **Seguridad de `Serializable`.** No se debe deserializar datos no confiables: un archivo manipulado puede ejecutar codigo o consumir recursos al reconstruir objetos. Este proyecto mitiga el riesgo con `ObjectInputFilter`; alternativas: JSON o un formato de texto propio.

## Notas

- Los mensajes de consola no llevan tildes a proposito, para que se vean bien en cualquier terminal.
- No hay semilla fija: el azar (criticos, habilidades de los enemigos) cambia en cada ejecucion.
- `partidas/` y `historial_combates.txt` se generan localmente y estan en el `.gitignore`.
