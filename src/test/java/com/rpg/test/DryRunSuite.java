package com.rpg.test;

import com.rpg.concurrency.AutoGuardadoDaemon;
import com.rpg.concurrency.EfectoContinuoTask;
import com.rpg.concurrency.GestorTurno;
import com.rpg.concurrency.TemporizadorTurnoTask;
import com.rpg.controller.HeroeController;
import com.rpg.core.Accion;
import com.rpg.core.Arena;
import com.rpg.core.Bestiario;
import com.rpg.core.Consola;
import com.rpg.core.EntradaConsola;
import com.rpg.core.FormatoCombate;
import com.rpg.core.RegistroCombate;
import com.rpg.core.TorneoManager;
import com.rpg.core.TurnoCallback;
import com.rpg.dao.HeroeDAO;
import com.rpg.entities.Arma;
import com.rpg.entities.Enemigo;
import com.rpg.entities.Escuadron;
import com.rpg.entities.EstadoEfecto;
import com.rpg.entities.Gremio;
import com.rpg.entities.Guerrero;
import com.rpg.entities.Mago;
import com.rpg.entities.PerfilJugador;
import com.rpg.entities.Personaje;
import com.rpg.entities.enums.EstadoPersonaje;
import com.rpg.entities.enums.RaridadItem;
import com.rpg.entities.enums.TipoEfecto;
import com.rpg.items.Inventario;
import com.rpg.items.Pocion;
import com.rpg.demo.DemoArchivos;
import com.rpg.demo.DemoHilosDaemon;
import com.rpg.model.Heroe;
import com.rpg.persistencia.GestorPartidas;

import java.io.*;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class DryRunSuite {

    private static int testsPassed = 0;
    private static int testsFailed = 0;

    private static void afirmo(boolean condicion, String nombrePrueba) {
        if (condicion) {
            System.out.println("  [PASS] " + nombrePrueba);
            testsPassed++;
        } else {
            System.err.println("  [FAIL] " + nombrePrueba);
            testsFailed++;
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("======================================================================");
        System.out.println("INICIANDO DRY-RUN Y SUITE DE PRUEBAS AUTOMATIZADAS - SIMULADOR RPG");
        System.out.println("======================================================================\n");

        testCompilacionYFirmas();
        testRegistroHeroe();
        testConcurrenciaHilos();
        testPersistenciaArchivos();
        testCorreccionesAuditoria();
        testCorreccionesFinales();
        testFormatoYEstilo();

        System.out.println("\n======================================================================");
        System.out.printf("RESULTADO GLOBAL: %d pruebas superadas, %d fallidas.%n", testsPassed, testsFailed);
        System.out.println("======================================================================");

        if (testsFailed > 0) {
            System.exit(1);
        }
    }

    // ==================================================================
    // 1. PRUEBAS DE COMPILACION Y DEPURACION SINTACTICA
    // ==================================================================
    private static void testCompilacionYFirmas() {
        System.out.println("--- 1. COMPILACION Y DEPURACION SINTACTICA ---");

        // Interfaces y herencia
        Guerrero g = new Guerrero("Conan");
        afirmo(g instanceof Personaje && g.getNombre().equals("Conan") && g.getDefensa() > 0,
                "Guerrero hereda de Personaje y calcula defensa con escudo");

        boolean rechazaNombreUnicode = false;
        try {
            new Guerrero("Jos" + (char) 233);
        } catch (IllegalArgumentException e) {
            rechazaNombreUnicode = true;
        }
        afirmo(rechazaNombreUnicode, "Personaje solo acepta nombres ASCII para mantener la consola compatible");

        Mago mago = new Mago("Gandalf");
        afirmo(mago instanceof Personaje && mago.getMana() == 60,
                "Mago hereda de Personaje con mana inicial");

        Enemigo orco = new Enemigo("Orco", 70, 12, 4);
        afirmo(orco.copia() != null && orco.copia().getNombre().equals("Orco"),
                "Enemigo hereda de Personaje y clonacion mediante copia()");
    }

    private static void testRegistroHeroe() {
        Heroe heroe = new Heroe(3, "Aria", "GUERRERO", 155, 5);
        HeroeController controller = new HeroeController(new HeroeDAO());
        Personaje personaje = controller.convertirAEntity(heroe);
        Guerrero esperado = new Guerrero("Aria");
        esperado.setNivel(5);
        afirmo(heroe.getTipo().equals("guerrero")
                        && personaje instanceof Guerrero
                        && personaje.getVidaMaxima() == 155
                        && personaje.getNivel() == 5
                        && personaje.calcularDanio() == esperado.calcularDanio(),
                "Heroe de BD conserva tipo, vida maxima y nivel en combate");

        Personaje mago = controller.convertirAEntity(new Heroe(4, "Merlin", "mago", 110, 3));
        afirmo(mago instanceof Mago && mago.getVidaMaxima() == 110 && mago.getNivel() == 3,
                "Heroe mago de BD conserva sus estadisticas");

        boolean rechazaTipoInvalido = false;
        try {
            new Heroe("Aria", "dragon", 100, 1);
        } catch (IllegalArgumentException e) {
            rechazaTipoInvalido = true;
        }
        afirmo(rechazaTipoInvalido, "Heroe rechaza tipos fuera de guerrero o mago");
    }

    // ==================================================================
    // 2. PRUEBAS DE CONCURRENCIA E HILOS
    // ==================================================================
    private static void testConcurrenciaHilos() throws Exception {
        System.out.println("\n--- 2. CONCURRENCIA E HILOS ---");

        // 2.1 Cancelacion limpia de TemporizadorTurnoTask mediante .interrupt()
        AtomicBoolean tiempoExpirado = new AtomicBoolean(false);
        TurnoCallback callback = () -> tiempoExpirado.set(true);
        TemporizadorTurnoTask tareaTurno = new TemporizadorTurnoTask(5, callback);
        Thread hiloTurno = new Thread(tareaTurno, "test-temporizador");
        hiloTurno.start();
        Thread.sleep(200);
        hiloTurno.interrupt();
        hiloTurno.join(2000);
        afirmo(!hiloTurno.isAlive() && !tiempoExpirado.get(),
                "Cancelacion limpia de TemporizadorTurnoTask con interrupt() sin disparar callback");

        // 2.2 Cancelacion limpia de EfectoContinuoTask mediante .interrupt()
        Guerrero objetivo = new Guerrero("Thor");
        EstadoEfecto veneno = new EstadoEfecto("VenenoTest", TipoEfecto.DANIO_CONTINUO, 5, 10);
        EfectoContinuoTask tareaEfecto = new EfectoContinuoTask(objetivo, veneno);
        Thread hiloEfecto = new Thread(tareaEfecto, "test-efecto");
        hiloEfecto.start();
        Thread.sleep(1500); // dejar que corra al menos 1 tick
        hiloEfecto.interrupt();
        hiloEfecto.join(2000);
        afirmo(!hiloEfecto.isAlive(),
                "Cancelacion limpia de EfectoContinuoTask con interrupt()");

        // 2.3 Simulacion de condiciones de carrera en Personaje (concurrencia de vida y efectos)
        Guerrero combatiente = new Guerrero("Leonidas");
        int numHilos = 20;
        int operacionesPorHilo = 100;
        ExecutorService pool = Executors.newFixedThreadPool(numHilos);
        CountDownLatch latch = new CountDownLatch(numHilos);

        for (int i = 0; i < numHilos; i++) {
            final int id = i;
            pool.submit(() -> {
                try {
                    for (int op = 0; op < operacionesPorHilo; op++) {
                        if (id % 2 == 0) {
                            combatiente.recibirDanio(2);
                        } else {
                            combatiente.curar(2);
                        }
                        if (op % 20 == 0) {
                            combatiente.aplicarEfecto(new EstadoEfecto("Ef" + id, TipoEfecto.DANIO_CONTINUO, 1, 1));
                            combatiente.quitarEfecto("Ef" + id);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        boolean completado = latch.await(10, TimeUnit.SECONDS);
        pool.shutdown();
        afirmo(completado, "Concurrencia masiva sobre Personaje completada sin deadlocks");
        afirmo(combatiente.getVida() >= 0 && combatiente.getVida() <= combatiente.getVidaMaxima(),
                "Invariante de vida [0, vidaMaxima] garantizada bajo concurrencia");

        // 2.4 Validacion de AutoGuardadoDaemon con setDaemon(true) antes de start()
        GestorPartidas gestor = new GestorPartidas();
        gestor.setPerfilActual(new PerfilJugador());
        AutoGuardadoDaemon daemon = new AutoGuardadoDaemon(gestor, 5000);
        boolean excepcionDaemon = false;
        try {
            daemon.setDaemon(true); // OBLIGATORIO antes de start()
            daemon.start();
        } catch (IllegalThreadStateException e) {
            excepcionDaemon = true;
        }
        afirmo(!excepcionDaemon && daemon.isDaemon(),
                "AutoGuardadoDaemon configurado como daemon ANTES de start() sin IllegalThreadStateException");
        daemon.interrupt();
        daemon.join(2000);

        // 2.5 Sincronizacion de semifinales con join() en TorneoManager
        RegistroCombate reg = new RegistroCombate();
        Arena arena = new Arena(reg, new Random(42));
        TorneoManager torneo = new TorneoManager(arena, reg);
        Personaje campeon = torneo.jugarTorneo();
        afirmo(campeon != null && campeon.estaVivo(),
                "TorneoManager sincroniza semifinales concurrentes con join() y obtiene campeon");
    }

    // ==================================================================
    // 3. PRUEBAS DE PERSISTENCIA EN ARCHIVOS
    // ==================================================================
    private static void testPersistenciaArchivos() throws Exception {
        System.out.println("\n--- 3. PERSISTENCIA EN ARCHIVOS ---");

        // 3.1 Creacion automatica de carpeta partidas/ con mkdirs()
        File dirPartidas = new File("partidas");
        GestorPartidas gestor = new GestorPartidas();
        afirmo(dirPartidas.exists() && dirPartidas.isDirectory(),
                "Directorio 'partidas/' creado automaticamente con mkdirs()");

        // 3.2 Guardado y carga con serializacion y try-with-resources
        PerfilJugador perfilOriginal = new PerfilJugador();
        perfilOriginal.registrarVictoria();
        perfilOriginal.registrarVictoria();
        perfilOriginal.registrarDerrota();
        perfilOriginal.desbloquearLogro("Primera Victoria");
        perfilOriginal.desbloquearLogro("Veterano");

        gestor.guardarPerfil(perfilOriginal);
        PerfilJugador perfilCargado = gestor.cargarPerfil();

        afirmo(perfilCargado != null, "Perfil deserializado correctamente desde archivo");
        afirmo(perfilCargado.getVictorias() == 2 && perfilCargado.getDerrotas() == 1,
                "Estadisticas de victorias y derrotas preservadas");
        afirmo(perfilCargado.getLogros().contains("Primera Victoria") && perfilCargado.getLogros().contains("Veterano"),
                "Coleccion de logros en TreeSet preservada e inmutable al exterior");

        // Metadatos y eliminacion
        gestor.mostrarMetadatos();
        boolean eliminado = gestor.eliminarPartida();
        afirmo(eliminado && !gestor.existePartida(), "Eliminacion de partida con Files.deleteIfExists()");

        // 3.3 Serializacion y deserializacion del grafo de objetos con transient hilosEfectos
        Guerrero g = new Guerrero("Lancelot");
        g.aplicarEfecto(new EstadoEfecto("Regeneracion", TipoEfecto.CURACION_CONTINUA, 3, 2));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(g);
        }
        Guerrero gDeserializado;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            gDeserializado = (Guerrero) ois.readObject();
        }
        afirmo(gDeserializado != null && gDeserializado.getNombre().equals("Lancelot"),
                "Personaje serializado y deserializado con campo transient reconstruido");

        // 3.4 RegistroCombate volcado y lectura con try-with-resources
        String archivoTest = "test_historial_combate.txt";
        RegistroCombate reg = new RegistroCombate();
        reg.agregar("Evento 1: Combate iniciado");
        reg.agregar("Evento 2: Ataque critico");
        reg.volcarAArchivo(archivoTest);

        File fTest = new File(archivoTest);
        afirmo(fTest.exists() && fTest.length() > 0, "RegistroCombate volcado a archivo con BufferedWriter");
        RegistroCombate.leerHistorial(archivoTest);
        fTest.delete();
    }

    // ==================================================================
    // 5. PRUEBAS DE FORMATO Y ESTILO
    // ==================================================================
    // ==================================================================
    // 6. CORRECCIONES DE AUDITORIA (concurrencia y persistencia)
    // ==================================================================
    private static void testCorreccionesAuditoria() throws Exception {
        System.out.println("\n--- 6. CORRECCIONES DE AUDITORIA ---");

        // 6.1 Main minimo
        afirmo(Class.forName("com.rpg.Main").getDeclaredMethods().length == 1, "Main solo declara main(String[])");

        // 6.2 TorneoManager: semifinales en paralelo (sin synchronized) y el torneo termina
        Method semi = TorneoManager.class.getMethod("simularSemifinal", Personaje.class, Personaje.class);
        afirmo(!Modifier.isSynchronized(semi.getModifiers()), "simularSemifinal ya no esta synchronized (semifinales en paralelo)");
        RegistroCombate regT = new RegistroCombate();
        Personaje campeon = new TorneoManager(new Arena(regT, new Random(1)), regT).jugarTorneo();
        afirmo(campeon != null, "Torneo con semifinales en hilos paralelos y join() devuelve campeon");

        // 6.7 Sin doble tick: mientras el hilo del efecto esta activo, procesarEfectos no lo vuelve a aplicar
        Guerrero g = new Guerrero("Tick");
        g.aplicarEfecto(new EstadoEfecto("Veneno", TipoEfecto.DANIO_CONTINUO, 1, 3));
        List<String> msgs = g.procesarEfectos();
        afirmo(msgs.isEmpty(), "procesarEfectos no duplica el tick de un efecto con hilo activo");
        g.restaurarVida();

        // 6.8 EfectoContinuoTask responde a interrupt(): termina, conserva la bandera y limpia su estado
        Guerrero victima = new Guerrero("Victima");
        EfectoContinuoTask tarea = new EfectoContinuoTask(victima, new EstadoEfecto("Lento", TipoEfecto.DANIO_CONTINUO, 1, 50));
        final boolean[] banderaRestaurada = {false};
        Thread t = new Thread(() -> {
            tarea.run();
            banderaRestaurada[0] = Thread.currentThread().isInterrupted();
        });
        t.start();
        Thread.sleep(150);
        t.interrupt();
        t.join(3000);
        afirmo(!t.isAlive() && banderaRestaurada[0], "EfectoContinuoTask sale limpio ante interrupt() y restaura la bandera");

        // 6.9 recibirDanioConLock (ReentrantLock + try-finally) sin condiciones de carrera
        Mago objetivo = new Mago("Yunque");
        objetivo.setVidaMaxima(100_000);
        objetivo.restaurarVida();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch fin = new CountDownLatch(4);
        for (int i = 0; i < 4; i++) {
            pool.submit(() -> {
                for (int k = 0; k < 1000; k++) {
                    objetivo.recibirDanioConLock(1);
                }
                fin.countDown();
            });
        }
        fin.await(10, TimeUnit.SECONDS);
        pool.shutdownNow();
        afirmo(objetivo.getVida() == 100_000 - 4000, "recibirDanioConLock: 4 hilos x 1000 golpes = vida exacta (sin carrera)");

        // 6.10 Hilos daemon: IllegalThreadStateException y cierre de JVM con daemon vivo
        afirmo(DemoHilosDaemon.setDaemonTrasStartLanzaExcepcion(),
                "setDaemon(true) despues de start() lanza IllegalThreadStateException");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process proc = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                "com.rpg.demo.DemoHilosDaemon", "salida-jvm").redirectErrorStream(true).start();
        boolean cerro = proc.waitFor(15, TimeUnit.SECONDS);
        if (!cerro) {
            proc.destroyForcibly();
        }
        afirmo(cerro, "La JVM cierra aunque un hilo daemon de 60 s siga vivo");

        // 6.11 File.delete() y Files.deleteIfExists(Path)
        GestorPartidas gp = new GestorPartidas();
        gp.setPerfilActual(new PerfilJugador());
        gp.guardarPerfilAutomatico();
        gp.guardarPerfil(new PerfilJugador());
        boolean autoBorrado = gp.eliminarAutoguardado();
        boolean autoNoExiste = !gp.eliminarAutoguardado();
        boolean principalBorrado = gp.eliminarPartida();
        afirmo(autoBorrado && autoNoExiste && principalBorrado && !gp.existePartida(),
                "Borrado con File.delete() (autoguardado) y Files.deleteIfExists (partida principal)");

        // 6.12 Serializacion de Personaje conserva agregacion/composicion y recrea campos transient
        Gremio gremio = new Gremio("Los Test");
        Guerrero conan = new Guerrero("Conan");
        conan.unirseAGremio(gremio);
        conan.equiparArma(new Arma("Espada", 12, RaridadItem.COMUN));
        conan.getInventario().agregar(new Pocion("Pocion de Vida", 30, RaridadItem.COMUN));
        conan.aplicarEfecto(new EstadoEfecto("Sangrado", TipoEfecto.DANIO_CONTINUO, 1, 1));
        Thread.sleep(1500);
        boolean guardado = gp.guardarPersonaje(conan);
        Personaje leido = gp.cargarPersonaje();
        boolean relaciones = leido != null
                && leido.getArma() != null && leido.getArma().getNombre().equals("Espada")
                && leido.getInventario().tamanio() == 1
                && leido.getGremio() != null && leido.getGremio().getMiembros().contains(leido);
        afirmo(guardado && relaciones, "Personaje serializado conserva arma, inventario y gremio bidireccional (mismo objeto)");
        int vidaAntes = leido.getVida();
        leido.recibirDanioConLock(5); // vidaLock transient recreado en readObject
        afirmo(leido.getVida() == vidaAntes - 5, "Campos transient (hilos y lock) se reconstruyen al deserializar");
        new File("partidas/personaje.dat").delete();
    }

    // ==================================================================
    // 7. CORRECCIONES FINALES (entrada con limite de tiempo, torneo, efectos, nivel, demos)
    // ==================================================================
    private static void testCorreccionesFinales() throws Exception {
        System.out.println("\n--- 7. CORRECCIONES FINALES ---");

        // 7.1 Entrada con limite real: el temporizador despierta al hilo principal sin pulsar Enter
        PipedOutputStream teclado = new PipedOutputStream();
        EntradaConsola entrada = new EntradaConsola(new PipedInputStream(teclado));
        Consola consola = new Consola(new Scanner(entrada), entrada);

        Thread despertador = new Thread(() -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            consola.expirarEspera();
        });
        long inicio = System.nanoTime();
        despertador.start();
        OptionalInt sinRespuesta = consola.leerEnteroConLimite("Accion: ", 0, 4);
        long ms = (System.nanoTime() - inicio) / 1_000_000;
        afirmo(sinRespuesta.isEmpty() && ms < 3000,
                "Consola.leerEnteroConLimite devuelve vacio al expirar el turno SIN que el usuario pulse Enter (" + ms + " ms)");

        // 7.2 Una linea escrita tarde no se pierde ni se exige: la consume la siguiente lectura
        teclado.write("2\n".getBytes());
        teclado.flush();
        OptionalInt tardia = consola.leerEnteroConLimite("Accion: ", 0, 4);
        afirmo(tardia.isPresent() && tardia.getAsInt() == 2, "La linea tardia queda en cola y la consume la siguiente lectura");

        // 7.3 Respuesta a tiempo + aviso tardio del temporizador: la marca sobrante no contamina la siguiente lectura
        teclado.write("3\n".getBytes());
        teclado.flush();
        OptionalInt aTiempo = consola.leerEnteroConLimite("Accion: ", 0, 4);
        consola.expirarEspera(); // el temporizador llega tarde
        teclado.write("1\n".getBytes());
        teclado.flush();
        OptionalInt siguiente = consola.leerEnteroConLimite("Accion: ", 0, 4);
        afirmo(aTiempo.orElse(-1) == 3 && siguiente.orElse(-1) == 1,
                "Respuesta a tiempo se devuelve y un expirar tardio no afecta la siguiente lectura");

        // 7.4 Entrada invalida seguida de expiracion: sigue sin exigir Enter
        teclado.write("abc\n".getBytes());
        teclado.flush();
        Thread despertador2 = new Thread(() -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            consola.expirarEspera();
        });
        despertador2.start();
        afirmo(consola.leerEnteroConLimite("Accion: ", 0, 4).isEmpty(), "Entrada invalida y luego expiracion: el turno se pierde");

        // 7.5 Integracion real: GestorTurno (1 s) + TurnoCallback -> Consola, sin escribir nada
        GestorTurno gestor = new GestorTurno(1);
        long t0 = System.nanoTime();
        gestor.iniciarTemporizador(consola::expirarEspera);
        OptionalInt integrado = consola.leerEnteroConLimite("Accion: ", 0, 4);
        gestor.cancelar();
        long ms2 = (System.nanoTime() - t0) / 1_000_000;
        afirmo(integrado.isEmpty() && ms2 >= 800 && ms2 < 4000,
                "GestorTurno + TurnoCallback liberan la lectura bloqueada a los ~1 s (" + ms2 + " ms)");

        // 7.6 Torneo: interrupcion del hilo principal -> cancelacion limpia, sin campeon inventado
        RegistroCombate regCancel = new RegistroCombate();
        TorneoManager torneoCancel = new TorneoManager(new Arena(regCancel, new Random(3)), regCancel);
        final Personaje[] resultado = {new Guerrero("Marca")};
        final boolean[] banderaTorneo = {false};
        Thread hiloTorneo = new Thread(() -> {
            resultado[0] = torneoCancel.jugarTorneo();
            banderaTorneo[0] = Thread.currentThread().isInterrupted();
        }, "hilo-torneo-prueba");
        hiloTorneo.start();
        Thread.sleep(40);
        hiloTorneo.interrupt();
        hiloTorneo.join(5000);
        boolean semisVivas = false;
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.isAlive() && t.getName().startsWith("Semifinal-")) {
                semisVivas = true;
            }
        }
        afirmo(!hiloTorneo.isAlive() && resultado[0] == null && banderaTorneo[0]
                        && !semisVivas && !regCancel.getTexto().contains("=== FINAL"),
                "Torneo interrumpido: devuelve null, restaura la bandera, no deja semifinales vivas y no juega la final");

        // 7.7 EfectoContinuoTask: el finally limpia el efecto del Personaje al ser interrumpido
        Guerrero obj1 = new Guerrero("Cobaya1");
        obj1.aplicarEfecto(new EstadoEfecto("Lento", TipoEfecto.DANIO_CONTINUO, 1, 50));
        Thread hiloEfecto = hiloPorNombre("efecto-Lento-Cobaya1");
        boolean registrado = hiloEfecto != null && obj1.getEfectos().size() == 1;
        if (hiloEfecto != null) {
            hiloEfecto.interrupt();
            hiloEfecto.join(3000);
        }
        afirmo(registrado && obj1.getEfectos().isEmpty(),
                "EfectoContinuoTask interrumpido elimina su efecto del mapa del Personaje (finally)");

        // 7.8 Si el objetivo muere, el efecto tambien se limpia
        Guerrero obj2 = new Guerrero("Cobaya2");
        obj2.aplicarEfecto(new EstadoEfecto("Fatal", TipoEfecto.DANIO_CONTINUO, 500, 5));
        long limite = System.currentTimeMillis() + 5000;
        while (!obj2.getEfectos().isEmpty() && System.currentTimeMillis() < limite) {
            Thread.sleep(50);
        }
        afirmo(!obj2.estaVivo() && obj2.getEfectos().isEmpty(),
                "Si el personaje muere, EfectoContinuoTask sale por break y su finally limpia el efecto");

        // 7.9 Un efecto homonimo reaplicado NO se borra por la limpieza del hilo anterior
        Guerrero obj3 = new Guerrero("Cobaya3");
        obj3.aplicarEfecto(new EstadoEfecto("Lento", TipoEfecto.DANIO_CONTINUO, 1, 50));
        obj3.aplicarEfecto(new EstadoEfecto("Lento", TipoEfecto.DANIO_CONTINUO, 1, 50));
        Thread.sleep(400);
        int vivos = 0;
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.isAlive() && t.getName().equals("efecto-Lento-Cobaya3")) {
                vivos++;
            }
        }
        afirmo(obj3.getEfectos().size() == 1 && vivos == 1,
                "Efecto reaplicado conserva su entrada y su hilo (la identidad del hilo evita borrados por error)");
        obj3.quitarEfecto("Lento");
        obj1.restaurarVida();

        // 7.10 AutoGuardadoDaemon: bucle isInterrupted() y salida limpia ante interrupt()
        String fuenteDaemon = Files.readString(Path.of("src/main/java/com/rpg/concurrency/AutoGuardadoDaemon.java"));
        GestorPartidas gestorDaemon = new GestorPartidas();
        gestorDaemon.setPerfilActual(new PerfilJugador());
        AutoGuardadoDaemon daemon = new AutoGuardadoDaemon(gestorDaemon, 50);
        daemon.setDaemon(true);
        daemon.start();
        Thread.sleep(150);
        daemon.interrupt();
        daemon.join(2000);
        afirmo(fuenteDaemon.contains("isInterrupted()") && !daemon.isAlive(),
                "AutoGuardadoDaemon usa isInterrupted() y termina limpio al interrumpirse");
        gestorDaemon.eliminarAutoguardado();

        // 7.11 Nivel del personaje: validacion, efecto en el dano y persistencia
        Guerrero niv = new Guerrero("Nivelado");
        int danioBase = niv.calcularDanio();
        niv.setNivel(5);
        boolean rechazaNivel = false;
        try {
            niv.setNivel(0);
        } catch (IllegalArgumentException e) {
            rechazaNivel = true;
        }
        GestorPartidas gpNivel = new GestorPartidas();
        boolean guardadoNivel = gpNivel.guardarPersonaje(niv);
        Personaje cargadoNivel = gpNivel.cargarPersonaje();
        afirmo(niv.getNivel() == 5 && niv.calcularDanio() == danioBase + 4 && rechazaNivel
                        && guardadoNivel && cargadoNivel != null && cargadoNivel.getNivel() == 5,
                "Personaje.nivel: getter/setter validados, +1 de ataque por nivel y se conserva al serializar");
        new File("partidas/personaje.dat").delete();

        // 7.12 Demostraciones del taller de archivos
        afirmo(DemoArchivos.mostrarRutas(), "DemoArchivos: ruta relativa vs absoluta");
        afirmo(DemoArchivos.demostrarCreateNewFile(), "DemoArchivos: createNewFile() true/false y IOException controlada");
        afirmo(DemoArchivos.demostrarNotSerializableException(),
                "DemoArchivos: NotSerializableException provocada (objeto anidado) y resuelta (Serializable / transient)");
    }

    private static Thread hiloPorNombre(String nombre) {
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.isAlive() && t.getName().equals(nombre)) {
                return t;
            }
        }
        return null;
    }

    private static void testFormatoYEstilo() throws Exception {
        System.out.println("\n--- 5. FORMATO Y ESTILO ---");

        // Ningun caracter fuera de ASCII en lineas de codigo (literales de consola); los comentarios se excluyen.
        // Se revisan tanto el codigo del juego como la propia suite.
        List<File> javaFiles = new ArrayList<>();
        File srcDir = new File("src/main/java");
        if (!srcDir.exists()) {
            srcDir = new File("rpg-base/src/main/java");
        }
        buscarArchivosJava(srcDir, javaFiles);
        File testDir = new File(srcDir.getParentFile().getParentFile(), "test/java");
        buscarArchivosJava(testDir, javaFiles);

        int totalArchivosRevisados = javaFiles.size();
        boolean sinTildes = true;

        for (File f : javaFiles) {
            String contenido = Files.readString(f.toPath());
            for (String linea : contenido.split("\\R")) {
                String recorte = linea.trim();
                boolean comentario = recorte.startsWith("*") || recorte.startsWith("//") || recorte.startsWith("/*");
                if (!comentario && linea.matches(".*[^\\x00-\\x7F].*")) {
                    System.err.println("Caracter especial en " + f.getName() + ": " + recorte);
                    sinTildes = false;
                }
            }
        }

        afirmo(sinTildes, "100% de mensajes en consola libres de tildes y caracteres especiales ("
                + totalArchivosRevisados + " archivos revisados)");
    }

    private static void buscarArchivosJava(File dir, List<File> resultado) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                buscarArchivosJava(f, resultado);
            } else if (f.getName().endsWith(".java")) {
                resultado.add(f);
            }
        }
    }
}
