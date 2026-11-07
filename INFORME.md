# INFORME TECNICO - Concurrencia y persistencia en archivos

## 7. Correcciones de concurrencia y persistencia

| Tema | Correccion |
|---|---|
| `TorneoManager` | `simularSemifinal` ya no es `synchronized`: las dos semifinales corren en paralelo y solo se sincronizan con `join()`. `RegistroCombate` sigue siendo seguro para hilos. Una interrupcion del hilo principal cancela el torneo de forma determinista (seccion 9.2). |
| Doble tick de efectos | `procesarEfectos()` ignora los efectos que ya tienen un `EfectoContinuoTask` vivo. El hilo avisa su fin con `efectoTerminado(...)`, que solo limpia si el hilo sigue registrado (evita borrar un efecto reaplicado). |
| `EfectoContinuoTask` | Ante `InterruptedException` restaura la bandera con `interrupt()` y sale. La limpieza esta centralizada en un `finally` (ver seccion 9.3): finaliza su copia y llama a `Personaje.efectoTerminado`, que retira el efecto solo si el hilo sigue registrado. Cubre fin normal, muerte del objetivo e interrupcion. |
| `ReentrantLock` | `Personaje.recibirDanioConLock` con `lock()` / `try-finally` / `unlock()`. |
| Hilos daemon | `com.rpg.demo.DemoHilosDaemon` demuestra `IllegalThreadStateException` (setDaemon tras start) y el cierre de la JVM con un daemon vivo. |
| Borrado | Autoguardado con `File.delete()` (solo `boolean`) y partida principal con `Files.deleteIfExists(Path)` (distingue "no existia" de error via `IOException`). |
| Serializacion | `GestorPartidas.guardarPersonaje/cargarPersonaje` conservan arma, inventario y gremio; `hilosEfectos` y `vidaLock` son `transient` y se recrean en `readObject`. Deserializacion con `ObjectInputFilter` (`com.rpg.**;java.base/*;!*`). |
| Acceso concurrente del daemon | Escritura/lectura de `.dat` bajo `synchronized(lock)`, perfil serializado bajo `synchronized(perfil)` y `PerfilJugador` con metodos `synchronized`; el historial `.txt` usa su propio cerrojo. |

## 8. Teoria y demostraciones del taller de archivos

Codigo ejecutable en `com.rpg.demo.DemoArchivos` (probado en `DryRunSuite`).

### 8.1 Ruta relativa vs ruta absoluta en `File`

- Una ruta **relativa** (`partidas/demo.tmp`) se interpreta a partir del directorio de trabajo del proceso (`user.dir`):
  el mismo texto apunta a archivos distintos segun desde donde se ejecute el programa (IntelliJ, terminal, un `.jar`).
- Una ruta **absoluta** (`/home/ana/rpg-base/partidas/demo.tmp` o `C:\rpg\partidas\demo.tmp`) identifica el archivo
  sin depender del directorio de trabajo.
- `getPath()` devuelve la ruta tal como se construyo; `getAbsolutePath()` la resuelve contra `user.dir`; `isAbsolute()` indica de que tipo es.

```java
File relativa = new File("partidas", "demo_rutas.tmp");
File absoluta = relativa.getAbsoluteFile();
System.out.println(relativa.getPath() + " -> isAbsolute=" + relativa.isAbsolute());   // false
System.out.println(absoluta.getPath() + " -> isAbsolute=" + absoluta.isAbsolute());   // true
```

### 8.2 `File.createNewFile()` con manejo de `IOException`

`createNewFile()` devuelve `true` si creo el archivo, `false` si ya existia y lanza `IOException` si no puede crearlo
(por ejemplo, si la carpeta padre no existe o no hay permisos). La comprobacion y la creacion son una sola operacion atomica.

```java
File archivo = new File("partidas", "demo_createNewFile.tmp");
try {
    boolean creado = archivo.createNewFile();          // true la primera vez
    System.out.println("Creado: " + creado);
    System.out.println("2a vez: " + archivo.createNewFile());   // false: ya existe
} catch (IOException e) {
    System.out.println("No se pudo crear el archivo: " + e.getMessage());
}

// Error provocado: la carpeta padre no existe
try {
    new File("carpeta_que_no_existe_xyz", "datos.tmp").createNewFile();
} catch (IOException e) {
    System.out.println("IOException esperada: " + e.getMessage());   // No such file or directory
}
```

### 8.3 Para que sirve `serialVersionUID`

Es el **identificador de version** de una clase serializable. Al serializar, Java escribe ese numero junto con los datos; al
deserializar compara el valor guardado con el de la clase actual y, si no coinciden, lanza `InvalidClassException`: asi se
evita reconstruir un objeto con una definicion de clase que ya no corresponde a los bytes guardados.

- Si **no** se declara, Java lo calcula a partir de la estructura de la clase, y casi cualquier cambio (agregar un metodo)
  lo modifica e invalida los archivos viejos.
- Si **se declara** (`private static final long serialVersionUID = 1L;`), los cambios compatibles (agregar un campo) siguen
  leyendo archivos antiguos, y el desarrollador decide conscientemente cuando subir el numero (cambio incompatible).
- Ejemplo real de este proyecto: se agrego el campo `nivel` a `Personaje` sin cambiar el UID; `readObject` lo corrige a 1 cuando
  el archivo viejo no lo traia.

### 8.4 `NotSerializableException`: provocarla y resolverla

Ocurre cuando una clase `Serializable` **compone** (tiene como campo) un objeto cuya clase no implementa `Serializable`.

```java
class Mochila { int capacidad; }                         // NO serializable

class HeroeDefectuoso implements Serializable {
    private static final long serialVersionUID = 1L;
    String nombre;
    Mochila mochila;                                      // objeto anidado no serializable
}

try (ObjectOutputStream oos = new ObjectOutputStream(new ByteArrayOutputStream())) {
    oos.writeObject(new HeroeDefectuoso());
} catch (NotSerializableException e) {
    System.out.println("Provocada por: " + e.getMessage());   // ...Mochila
}
```

Dos soluciones:

```java
// A) el objeto anidado tambien es Serializable
class MochilaSerializable implements Serializable {
    private static final long serialVersionUID = 1L;
    int capacidad;
}

// B) el campo no debe guardarse: transient (vuelve null/0 al deserializar y se recrea si hace falta)
class HeroeConTransient implements Serializable {
    private static final long serialVersionUID = 1L;
    String nombre;
    transient Mochila mochila;
}
```

En el proyecto real se aplica la solucion B a los hilos (`hilosEfectos`) y al `ReentrantLock` de `Personaje`, que no se pueden serializar.

### 8.5 `FileWriter` vs `Files.writeString` (corregido)

| | `BufferedWriter` + `FileWriter` | `Files.writeString` (NIO.2) |
|---|---|---|
| Uso | Flujo que abres, escribes varias veces y cierras (`try-with-resources`) | Una sola llamada que escribe todo el texto |
| Buffer | Si (se descarga al llenarse, con `flush()` o al cerrar) | No: escribe el contenido completo |
| Append | `new FileWriter(file, true)` | `StandardOpenOption.APPEND` |
| Ideal para | Logs que crecen linea a linea (historial de combates) | Textos pequenos o medianos que ya estan completos en memoria |
| Atomicidad | No | **No garantizada a nivel del sistema operativo** |

`Files.writeString` abre, escribe y cierra el archivo, pero **no es una operacion atomica del sistema operativo**: si el proceso
termina o la maquina falla a mitad de la escritura, el archivo puede quedar truncado o incompleto, y otro proceso podria leerlo a
medias. Para un reemplazo "todo o nada" se escribe primero a un archivo temporal y despues se usa
`Files.move(temporal, destino, StandardCopyOption.ATOMIC_MOVE)`. Tampoco conviene llamarlo en cada linea de un log: abriria y
cerraria el archivo cada vez.

### 8.6 `File.delete()` vs `Files.deleteIfExists(Path)`

`File.delete()` solo devuelve un `boolean` (no distingue "no existia" de "no se pudo borrar"); se usa para el autoguardado.
`Files.deleteIfExists(Path)` devuelve `true` si borro, `false` si no existia y lanza `IOException` si hubo un error real
(permisos, carpeta no vacia); se usa para la partida principal en `GestorPartidas`.

## 9. Correcciones de la auditoria final

### 9.1 Temporizador bloqueado en `Scanner.nextLine()`

**Problema.** `TemporizadorTurnoTask` avisaba el fin del turno, pero el hilo principal seguia bloqueado en `Scanner.nextLine()`
(no se puede interrumpir) hasta que el usuario pulsara Enter.

**Solucion.** `EntradaConsola` (un `InputStream`) lee `System.in` en un hilo daemon y publica **una linea por elemento** en una
`BlockingQueue`. El `Scanner` se construye sobre ella, de modo que el resto de las vistas funciona igual. Para la lectura con
limite, el `TurnoCallback` del temporizador llama a `Consola.expirarEspera()`, que publica una marca especial en la misma cola;
`Consola.leerEnteroConLimite` la recibe, devuelve `OptionalInt.empty()` y `MotorJuego.pedirAccion` devuelve `null` (turno perdido).

```
 Hilo principal                 Hilo entrada-consola            Hilo temporizador
 pedirAccion()                  System.in --> cola              (cuenta 15..1)
   leerEnteroConLimite
   esperarLinea() --take()----> [ "2\n" ]  --> devuelve 2        .interrupt() al responder
          |                                                    onTiempoAgotado()
          +--take()<-- [TIEMPO] <------------------------------  consola.expirarEspera()
   -> empty -> turno perdido -> ataca el enemigo (sin pulsar Enter)
```

Una linea escrita tarde no se descarta: queda en la cola y la consume la siguiente lectura. Una marca de tiempo sobrante
(el temporizador llego justo despues de responder) se elimina al empezar y al terminar cada lectura.

### 9.2 Torneo y `InterruptedException`

Todo el bloque de espera (pausa de monitoreo + `join()` de A + `join()` de B) esta en **un solo** `try`. Si el hilo principal se
interrumpe en cualquier punto, `cancelarTorneo` interrumpe las dos semifinales, espera a que terminen (sin hilos sueltos),
restaura la bandera de interrupcion, imprime "Torneo CANCELADO" y devuelve `null`. No se lee `finalistas[]` ni se inventa un
ganador; `MotorJuego.jugarTorneo` registra "TORNEO CANCELADO" en lugar de un campeon. Si una semifinal termina sin resultado
tampoco se juega la final.

### 9.3 `EfectoContinuoTask`: limpieza en `finally`

```java
try {
    while (!hiloActual.isInterrupted()) { /* tick, consumir turno, sleep */ }
} finally {
    efecto.finalizar();
    objetivo.efectoTerminado(nombreEfecto);   // solo borra si el hilo registrado es ESTE hilo
}
```

`Personaje.efectoTerminado` compara `hilosEfectos.get(nombre) == Thread.currentThread()`. Cuando un efecto se reaplica,
`aplicarEfecto` ya reemplazo la entrada por el hilo nuevo antes de interrumpir al anterior, asi que la limpieza del hilo viejo
no borra el efecto nuevo. Cubre los tres finales: ticks agotados, muerte del objetivo e interrupcion.

### 9.4 Otros ajustes

- `AutoGuardadoDaemon.run()` usa `while (true)` y sale con `break` desde el `catch (InterruptedException)`.
- `Personaje.nivel` (getter y setter validado >= 1, por defecto 1): cada nivel por encima del 1 suma 1 punto a `calcularDanio()`.
  `Personaje.nivel` se muestra en "Ver estado" y se conserva al serializar.
- `DryRunSuite`: el mensaje con la raya larga ahora usa `-` y la prueba de estilo revisa **todo caracter no ASCII fuera de
  comentarios**, en el codigo del juego y en la propia suite.

## 10. Matriz de hallazgos y solucion

| # | Hallazgo | Solucion | Verificacion (`DryRunSuite`) |
|---|---|---|---|
| 1 | Temporizador no libera `Scanner.nextLine()` | `EntradaConsola` + `Consola.leerEnteroConLimite` + `TurnoCallback -> expirarEspera` (9.1) | 7.1 a 7.5 |
| 2 | Torneo no espera las dos semifinales tras interrupcion | Un solo `try`; `cancelarTorneo`; devuelve `null` (9.2) | 7.6 |
| 3 | `EfectoContinuoTask` no elimina el efecto al interrumpirse | `finally` + `efectoTerminado` (9.3) | 7.7 |
| 4 | `EfectoContinuoTask` no llama a `efectoTerminado` si el objetivo muere | Mismo `finally` | 7.8 |
| 5 | Riesgo de borrar un efecto homonimo reaplicado | Comparacion de identidad del hilo | 7.9 |
| 6 | `AutoGuardadoDaemon` sin `while (true)` literal | `while (true)` + `break` en el `catch` | 7.10 |
| 7 | Nivel del personaje no se aplicaba | `Personaje.nivel` | 7.11 |
| 8 | Falta explicar ruta relativa vs absoluta | Seccion 8.1 + `DemoArchivos.mostrarRutas` | 7.12 |
| 9 | Falta demostrar `createNewFile()` con `IOException` | Seccion 8.2 + `DemoArchivos.demostrarCreateNewFile` | 7.12 |
| 10 | Falta explicar `serialVersionUID` | Seccion 8.3 | - |
| 11 | Falta provocar y resolver `NotSerializableException` | Seccion 8.4 + `DemoArchivos.demostrarNotSerializableException` | 7.12 |
| 12 | Explicacion de `FileWriter` vs `Files.writeString` sin aclarar atomicidad | Seccion 8.5 y Javadoc de `RegistroCombate` corregidos | - |
| 13 | README con informacion de funciones retiradas | `README.md` actualizado a la version actual del proyecto | - |
| 14 | Caracter `-` largo (raya) en un mensaje de consola de `DryRunSuite` | Sustituido por `-` ASCII; prueba de estilo ampliada | 5 (estilo) |

