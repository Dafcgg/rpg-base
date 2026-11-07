# INFORME TECNICO — Unidad 5.2: Arena Segura (JDBC + MVC)

---

## 1. Diagrama de Flujo: Autenticacion View → Controller → DAO → BD

```
  USUARIO
    │
    ▼
┌─────────────────────────────────────────────────────────┐
│  VIEW: LoginUI                                          │
│  - Lee usuario y contrasena con Scanner                 │
│  - Llama: jugadorController.login(usuario, clave)       │
└───────────────────────┬─────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────┐
│  CONTROLLER: JugadorController                          │
│  - Valida que campos no esten vacios                    │
│  - Aplica Jugador.hashSHA256(clave)                     │
│  - Controla contador intentos (max 3, bloquea sesion)   │
│  - Llama: jugadorDAO.autenticar(usuario, hash)          │
└───────────────────────┬─────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────┐
│  DAO: JugadorDAO                                        │
│  - Obtiene Connection desde ConexionDB                  │
│  - PreparedStatement: SELECT … WHERE usuario=? AND      │
│    clave=?  (parametros ?, nunca concatenacion)         │
│  - Ejecuta executeQuery()                               │
│  - Mapea ResultSet → Jugador POJO                       │
│  - Cierra todos los recursos en try-with-resources      │
└───────────────────────┬─────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────┐
│  BASE DE DATOS: MySQL (rpg_arena_db.jugadores)          │
│  - Consulta con indice en columna 'usuario'             │
│  - Compara hash SHA-256 almacenado                      │
│  - Devuelve fila si coincide, vacio si no               │
└─────────────────────────────────────────────────────────┘
                        │
           Jugador POJO (o null si falla)
                        │
                        ▼
         Controller → View: muestra resultado
```

---

## 2. Benchmark Comparativo: Statement vs PreparedStatement

> **Prueba hipotetica**: 1000 inserciones en tabla `monstruos` en bucle.
> Maquina de referencia: Intel Core i7 / MySQL 8.4.0 local / Java 21.

| Criterio                  | Statement (inseguro)         | PreparedStatement (seguro)      |
|---------------------------|------------------------------|---------------------------------|
| **Seguridad**             | ❌ Vulnerable a SQL Injection | ✅ Inmune: valores como literales |
| **Precompilacion**        | ❌ Recompila cada iteracion  | ✅ Compilada 1 vez, reutilizada  |
| **Tiempo 1000 INSERT**    | ~1420 ms                     | ~610 ms                          |
| **Tiempo promedio/INSERT**| ~1.42 ms                     | ~0.61 ms                         |
| **Mejora de rendimiento** | referencia                   | **~57% mas rapido**              |
| **Cache del servidor BD** | No aprovecha plan de ejecucion | Reutiliza el plan cacheado     |
| **Tipos de datos**        | Manual (conversion a String) | Automatico (`setInt`, `setString`)|
| **Codigo legible**        | Consulta mezclada con datos  | Plantilla clara separada         |

**Conclusion**: `PreparedStatement` es superior en seguridad Y rendimiento. No hay razon para usar `Statement` en codigo de produccion excepto DDL o consultas sin parametros.

---

## 3. executeQuery vs executeUpdate

| Metodo           | Tipo de sentencia SQL        | Valor de retorno                | Modifica datos |
|------------------|------------------------------|---------------------------------|----------------|
| `executeQuery()` | `SELECT`                     | `ResultSet` con filas resultado | No             |
| `executeUpdate()`| `INSERT`, `UPDATE`, `DELETE` | `int` = numero de filas afectadas | Si           |

**Ejemplo en este proyecto:**
```java
// executeQuery → SELECT en JugadorDAO.autenticar()
try (ResultSet rs = ps.executeQuery()) {
    if (rs.next()) { /* mapear fila */ }
}

// executeUpdate → INSERT en JugadorDAO.registrar()
int filas = ps.executeUpdate(); // 1 si exitoso, 0 si fallo
return filas > 0;
```

---

## 4. Bitacora: Error Critico Encontrado y Resuelto

### Error: `ResultSet` cerrado al retornar desde el DAO

**Descripcion del problema:**

Durante el diseno inicial de `VictoriaDAO.victoriasDeJugador()`, se tenia la intencion de retornar el `ResultSet` directamente al Controller para que este lo procesara. Sin embargo, al cerrar la conexion al salir del metodo, el `ResultSet` quedaba automaticamente invalidado (ya que un `ResultSet` no puede sobrevivir al cierre de su `Connection` o `Statement`).

**Sintoma:**
```
java.sql.SQLException: Operation not allowed after ResultSet closed
    at com.mysql.cj.jdbc.result.ResultSetImpl.checkClosed(...)
    at ...VictoriaController.obtenerHistorialPropio(...)
```

**Causa raiz:**

Violacion de la separacion de capas MVC: intentar usar un recurso JDBC (`ResultSet`) fuera de la capa DAO.

**Solucion aplicada:**

Se modifico `VictoriaDAO.victoriasDeJugador()` para que consuma el `ResultSet` internamente dentro del bloque `try-with-resources`, mapee cada fila a un objeto `VictoriaConNombre` (POJO) y retorne una `List<Victoria>` completamente desacoplada de JDBC. El Controller y la View solo ven POJOs Java estandar, nunca recursos JDBC.

```java
// ANTES (incorrecto — expone ResultSet fuera del DAO)
public ResultSet victoriasDeJugador(String usuario) { ... }

// DESPUES (correcto — retorna lista de POJOs)
public List<Victoria> victoriasDeJugador(String usuario) {
    List<Victoria> lista = new ArrayList<>();
    try (Connection conn = ...; PreparedStatement ps = ...; ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
            lista.add(mapearFila(rs)); // POJO creado dentro del try-with-resources
        }
    }
    return lista; // ResultSet ya cerrado; lista es independiente de JDBC
}
```

**Leccion aprendida:** La capa DAO es la unica responsable de abrir Y cerrar todos los recursos JDBC. Nunca retornar `ResultSet`, `Connection` o `PreparedStatement` abiertos a capas superiores.

---

*Autor: Arquitecto de Software — Unidad 5.2 Arena Segura | Proyecto: rpg-base*

---

## 5. Registro de Heroes persistente (rpg_grupal_db)

Base de datos `rpg_grupal_db`, tabla `heroes` (ver `schema_grupal.sql`). La conexion usa la clave
`url_grupal` de `config.txt`; solo `ConexionDB` lee ese archivo.

### 5.1 Diagrama de secuencia: insertar heroe (View -> Controller -> DAO -> BD)

```
 Usuario        HeroeUI (View)      HeroeController        HeroeDAO            ConexionDB        MySQL
    |                 |                    |                   |                    |               |
    | opcion 1        |                    |                   |                    |               |
    |---------------->|                    |                   |                    |               |
    | nombre,tipo,    |                    |                   |                    |               |
    | vida,nivel      |                    |                   |                    |               |
    |---------------->|                    |                   |                    |               |
    |                 | insertar(nombre,   |                   |                    |               |
    |                 |  tipo,vida,nivel)  |                   |                    |               |
    |                 |------------------->|                   |                    |               |
    |                 |                    | valida: nombre no |                    |               |
    |                 |                    | vacio, tipo valido|                    |               |
    |                 |                    | vida>0, nivel>=1  |                    |               |
    |                 |                    |--[invalido]--> mensaje a la View, FIN  |               |
    |                 |                    | new Heroe(...)    |                    |               |
    |                 |                    | insertar(heroe)   |                    |               |
    |                 |                    |------------------>|                    |               |
    |                 |                    |                   | obtenerConexion-   |               |
    |                 |                    |                   | Grupal()           |               |
    |                 |                    |                   |------------------->| lee config.txt|
    |                 |                    |                   |                    | getConnection |
    |                 |                    |                   |                    |-------------->|
    |                 |                    |                   |<-------Connection--|               |
    |                 |                    |                   | prepareStatement(  |               |
    |                 |                    |                   |  INSERT ... ?,?,?,?)               |
    |                 |                    |                   | executeUpdate()----------------->  |
    |                 |                    |                   |<------------ filas afectadas = 1 --|
    |                 |                    |                   | getGeneratedKeys -> id             |
    |                 |                    |                   | close (try-with-resources)         |
    |                 |                    |<--- true / false--|                    |               |
    |                 |                    | decide el mensaje:|                    |               |
    |                 |                    | "creado con id=N" |                    |               |
    |                 |<-------------------|                   |                    |               |
    |<--- resultado --|                    |                   |                    |               |
```

Reglas respetadas: la View no importa `java.sql` ni DAO; el Controller solo valida y coordina; solo el
DAO usa JDBC. `actualizarNivel` y `eliminar` devuelven las filas afectadas y, si son 0, el Controller
informa "no existe un heroe con ese id".

### 5.2 Cargar heroe desde BD

`HeroeUI.cargarYCombatir()` -> `HeroeController.crearPersonaje(id)` -> `HeroeDAO.obtenerPorId(id)`.
Segun `tipo` se instancia un `Guerrero` o un `Mago`, se ajusta su vida maxima con la de la fila y se
entrega a `MotorJuego.combateRapidoCon(personaje)`.

## 6. Demo didactica de inyeccion SQL (aislada en este informe)

El codigo activo ya no concatena datos del usuario en SQL; la demo vive solo aqui como texto.

```
-- VULNERABLE (Statement + concatenacion), NO usar:
String sql = "SELECT * FROM jugadores WHERE usuario = '" + u + "' AND clave = '" + c + "'";
-- Entrada:  usuario = admin' --      clave = lo que sea
-- SQL real: SELECT * FROM jugadores WHERE usuario = 'admin' --' AND clave = '...'
--           (el comentario -- anula la comprobacion de la clave)

-- SEGURO (PreparedStatement):
PreparedStatement ps = conn.prepareStatement(
        "SELECT * FROM jugadores WHERE usuario = ? AND clave = ?");
ps.setString(1, u);   // el valor viaja como dato, nunca como SQL
ps.setString(2, c);
```

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
  `HeroeController.crearPersonaje` lo asigna desde la fila de la BD; se muestra en "Ver estado" y se conserva al serializar.
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
| 7 | Nivel del heroe no se aplicaba | `Personaje.nivel`, `HeroeController.crearPersonaje` | 6.3, 7.11 |
| 8 | Falta explicar ruta relativa vs absoluta | Seccion 8.1 + `DemoArchivos.mostrarRutas` | 7.12 |
| 9 | Falta demostrar `createNewFile()` con `IOException` | Seccion 8.2 + `DemoArchivos.demostrarCreateNewFile` | 7.12 |
| 10 | Falta explicar `serialVersionUID` | Seccion 8.3 | - |
| 11 | Falta provocar y resolver `NotSerializableException` | Seccion 8.4 + `DemoArchivos.demostrarNotSerializableException` | 7.12 |
| 12 | Explicacion de `FileWriter` vs `Files.writeString` sin aclarar atomicidad | Seccion 8.5 y Javadoc de `RegistroCombate` corregidos | - |
| 13 | README obsoleto, checklist sin marcar y sin advertencia de `DROP TABLE` | `README.md` reescrito con todo en `[x]` y advertencia visible | - |
| 14 | Caracter `-` largo (raya) en un mensaje de consola de `DryRunSuite` | Sustituido por `-` ASCII; prueba de estilo ampliada | 5 (estilo) |

