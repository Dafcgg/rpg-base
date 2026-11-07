package com.rpg.entities;

import com.rpg.concurrency.EfectoContinuoTask;
import com.rpg.entities.enums.EstadoPersonaje;
import com.rpg.entities.enums.TipoPersonaje;
import com.rpg.exceptions.SinManaException;
import com.rpg.items.Inventario;
import com.rpg.items.Pocion;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Clase base de todos los personajes del juego.
 *
 * <h2>Serialización</h2>
 * <p>Implementa {@link Serializable} con {@code serialVersionUID = 1L}. Los campos
 * {@code hilosEfectos} se marcan como {@code transient} porque {@link Thread} no es
 * serializable. Al deserializar, {@code readObject} inicializa ese mapa vacío.</p>
 *
 * <h2>Sincronización — synchronized vs ReentrantLock</h2>
 * <p>Los métodos críticos ({@code recibirDanio}, {@code curar}, {@code setVida},
 * {@code aplicarEfecto}, {@code quitarEfecto}) usan {@code synchronized(this)} para
 * proteger la vida, el estado y el mapa de efectos ante accesos concurrentes desde
 * {@link EfectoContinuoTask}.</p>
 *
 * <p>A continuación se documenta la alternativa con {@link ReentrantLock}:</p>
 * <pre>{@code
 * // Alternativa con ReentrantLock en recibirDanio:
 * private final transient ReentrantLock vidaLock = new ReentrantLock();
 *
 * public void recibirDanio(int cantidad) {
 *     vidaLock.lock();             // adquiere el lock
 *     try {
 *         setVida(vida - cantidad);
 *     } finally {
 *         vidaLock.unlock();       // SIEMPRE libera en finally para evitar deadlocks
 *     }
 * }
 * }</pre>
 * <p>Ventajas de {@code ReentrantLock} sobre {@code synchronized}:
 * <ul>
 *   <li>Permite {@code tryLock()} con timeout (evita deadlocks en sistemas complejos).</li>
 *   <li>Permite {@code lockInterruptibly()} para cancelar la espera con {@code interrupt()}.</li>
 *   <li>Soporta condiciones ({@code Condition}) para {@code await()}/{@code signal()}.</li>
 * </ul>
 * La desventaja es mayor verbosidad y el riesgo de olvidar el {@code unlock()} en {@code finally}.
 * La version implementada esta en {@link #recibirDanioConLock(int)}; el mecanismo principal sigue siendo
 * {@code synchronized}.</p>
 *
 * <p>{@code AtomicInteger} no basta para esta entidad: solo haria atomica la actualizacion de
 * {@code vida}, pero no la transicion coordinada de {@code estado} ni los cambios relacionados
 * en el mapa {@code efectos}. El monitor comun protege esos invariantes como una sola operacion.</p>
 */
// TALLER ARCHIVOS: Personaje es Serializable con serialVersionUID. Los campos de hilo son transient.
public abstract class Personaje implements ICombatiente, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final int VIDA_BASE = 100;
    private static final int ATAQUE_BASE = 10;
    private static final int DEFENSA_BASE = 5;
    private static final int CAPACIDAD_INVENTARIO = 3;
    private static final int DANIO_MINIMO = 1;
    private static final double PORCENTAJE_TOTAL = 100.0;
    private static final String PATRON_NOMBRE = "[A-Za-z0-9 ]+";

    private String nombre;
    private final TipoPersonaje tipo;
    private int vidaMaxima;
    private int vida;
    private final int ataqueBase;
    private final int defensaBase;
    /** Nivel del personaje (>= 1). Cada nivel por encima del 1 suma 1 punto de ataque. */
    private int nivel = 1;
    private EstadoPersonaje estado = EstadoPersonaje.VIVO;

    private Arma arma;                  // asociacion 0..1
    private Gremio gremio;              // asociacion bidireccional 0..1
    private final Inventario<Pocion> inventario = new Inventario<>(CAPACIDAD_INVENTARIO); // composicion
    private final HashSet<String> habilidades = new HashSet<>();
    // LinkedHashMap es Serializable; EstadoEfecto también lo es.
    private final LinkedHashMap<String, EstadoEfecto> efectos = new LinkedHashMap<>();

    /**
     * Mapa de hilos de efectos continuos activos.
     * Es {@code transient} porque {@link Thread} no implementa {@link Serializable}.
     * Al deserializar, se reinicializa como mapa vacío mediante {@link #readObject}.
     */
    private transient Map<String, Thread> hilosEfectos;

    /** Cerrojo explicito para {@link #recibirDanioConLock}. No es serializable: se recrea al cargar. */
    private transient ReentrantLock vidaLock = new ReentrantLock();

    protected Personaje(String nombre, TipoPersonaje tipo) {
        this(nombre, tipo, VIDA_BASE, ATAQUE_BASE, DEFENSA_BASE);
    }

    protected Personaje(String nombre, TipoPersonaje tipo, int vidaMaxima, int ataqueBase, int defensaBase) {
        validarNombre(nombre);
        if (vidaMaxima <= 0 || ataqueBase < 0 || defensaBase < 0) {
            throw new IllegalArgumentException("Estadisticas invalidas para " + nombre);
        }
        this.nombre = nombre;
        this.tipo = tipo;
        this.vidaMaxima = vidaMaxima;
        this.vida = vidaMaxima;
        this.ataqueBase = ataqueBase;
        this.defensaBase = defensaBase;
        this.hilosEfectos = new HashMap<>();
    }

    private static void validarNombre(String nombre) {
        if (nombre == null || nombre.isBlank() || !nombre.matches(PATRON_NOMBRE)) {
            throw new IllegalArgumentException("El nombre solo puede contener letras ASCII, numeros y espacios");
        }
    }

    /**
     * Restaura campos {@code transient} al deserializar desde disco.
     * Sin este método, {@code hilosEfectos} sería {@code null} tras la carga.
     */
    @Serial
    private void readObject(java.io.ObjectInputStream in)
            throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();
        // Reinicializar el mapa de hilos (los hilos no se persisten)
        hilosEfectos = new HashMap<>();
        vidaLock = new ReentrantLock();
        if (nivel < 1) {
            nivel = 1; // partidas guardadas antes de existir el atributo nivel
        }
    }

    // ---- combate

    /**
     * Ataque especial propio de cada clase.
     * @return descripcion de lo ocurrido, para el registro de combate
     */
    public abstract String realizarAtaqueEspecial(Personaje objetivo) throws SinManaException;

    /** Hook que se ejecuta al comenzar el turno (por ejemplo, el mago regenera mana). */
    public void alIniciarTurno() {
    }

    public int calcularDanio() {
        double bruto = ataqueBase + (getNivel() - 1) + tipo.getBonificadorAtaque();
        if (arma != null) {
            bruto += arma.getDanioEfectivo();
        }
        return (int) Math.round(bruto);
    }

    @Override
    public void defender(int danoRecibido) {
        // Con enteros, 100 / 123 daria 0: por eso se divide con un double
        double factorMitigacion = PORCENTAJE_TOTAL / (PORCENTAJE_TOTAL + getDefensa());
        int danioFinal = (int) Math.round(danoRecibido * factorMitigacion);
        recibirDanio(Math.max(DANIO_MINIMO, danioFinal));
    }

    // ---- vida

    /**
     * Reduce la vida del personaje en la cantidad indicada, de forma segura para hilos.
     *
     * <h3>¿Por qué synchronized?</h3>
     * <p>Sin sincronización, dos hilos (p.ej. {@link EfectoContinuoTask} A y B) pueden
     * leer el mismo valor de {@code vida}, calcular sus restas por separado y escribir
     * resultados incorrectos (read-modify-write no atómico).
     * El monitor de {@code this} garantiza exclusión mutua.</p>
     *
     * <h3>Alternativa con ReentrantLock (documentada)</h3>
     * <pre>{@code
     * private final transient ReentrantLock vidaLock = new ReentrantLock();
     *
     * public void recibirDanio(int cantidad) {
     *     vidaLock.lock();
     *     try {
     *         if (cantidad < 0) throw new IllegalArgumentException("El danio no puede ser negativo");
     *         setVida(vida - cantidad);
     *     } finally {
     *         vidaLock.unlock(); // siempre en finally
     *     }
     * }
     * }</pre>
     *
     * @param cantidad puntos de daño a aplicar (debe ser ≥ 0)
     */
    public synchronized void recibirDanio(int cantidad) {
        if (cantidad < 0) {
            throw new IllegalArgumentException("El danio no puede ser negativo");
        }
        // TALLER CONCURRENCIA: protegido con synchronized para evitar race condition
        // entre hilos de EfectoContinuoTask y el hilo principal del juego.
        setVida(vida - cantidad);
    }

    /**
     * Version de {@link #recibirDanio} con {@link ReentrantLock} (investigacion obligatoria).
     *
     * <p>{@code unlock()} va SIEMPRE en el {@code finally}: si setVida lanzara una excepcion y el
     * unlock estuviera fuera, el lock quedaria tomado para siempre y cualquier otro hilo se
     * bloquearia indefinidamente.</p>
     *
     * <p>Cuando preferir ReentrantLock sobre synchronized: cuando se necesita {@code tryLock()}
     * con timeout, {@code lockInterruptibly()} para poder cancelar la espera con interrupt(),
     * equidad (FIFO) o varias {@code Condition}. Para una exclusion simple, synchronized es
     * mas corto y no se puede olvidar de liberar.</p>
     *
     * <p>Los demas metodos de la clase usan el monitor de {@code this}; por eso el cuerpo tambien
     * toma ese monitor, para que ambos mecanismos no se pisen entre si.</p>
     */
    public void recibirDanioConLock(int cantidad) {
        if (cantidad < 0) {
            throw new IllegalArgumentException("El danio no puede ser negativo");
        }
        vidaLock.lock();
        try {
            synchronized (this) {
                setVida(vida - cantidad);
            }
        } finally {
            vidaLock.unlock();
        }
    }

    /** @return vida realmente recuperada */
    public synchronized int curar(int cantidad) {
        if (cantidad < 0) {
            throw new IllegalArgumentException("La curacion no puede ser negativa");
        }
        if (!estaVivo()) {
            return 0;
        }
        int vidaAntes = vida;
        setVida(vida + cantidad);
        return vida - vidaAntes;
    }

    public synchronized void restaurarVida() {
        detenerTodosLosEfectosContinuos();
        efectos.clear();
        estado = EstadoPersonaje.VIVO;
        vida = vidaMaxima;
    }

    @Override
    public synchronized boolean estaVivo() {
        return estado != EstadoPersonaje.MUERTO;
    }

    // ---- efectos DoT / HoT

    /**
     * Aplica un efecto continuo sobre el personaje lanzando un {@link EfectoContinuoTask}
     * en un hilo dedicado.
     *
     * <p>El método es {@code synchronized} para proteger el mapa {@code efectos} y el
     * mapa {@code hilosEfectos} de modificaciones concurrentes.</p>
     */
    public synchronized void aplicarEfecto(EstadoEfecto efecto) {
        // Detener hilo previo del mismo efecto si existe
        detenerEfectoContinuo(efecto.getNombre());

        efectos.put(efecto.getNombre(), efecto.copia());
        if (efecto.esDanio() && estado == EstadoPersonaje.VIVO) {
            estado = EstadoPersonaje.ENVENENADO;
        }

        // Lanzar hilo de efecto continuo
        if (hilosEfectos == null) {
            hilosEfectos = new HashMap<>();
        }
        EfectoContinuoTask tarea = new EfectoContinuoTask(this, efecto);
        Thread hilo = new Thread(tarea, "efecto-" + efecto.getNombre() + "-" + nombre);
        hilo.setDaemon(true);
        hilo.start();
        hilosEfectos.put(efecto.getNombre(), hilo);
    }

    public synchronized boolean quitarEfecto(String nombreEfecto) {
        if (efectos.remove(nombreEfecto) == null) {
            return false;
        }
        detenerEfectoContinuo(nombreEfecto);
        normalizarEstado();
        return true;
    }

    public synchronized void quitarEfectosDanio() {
        for (String nombreEfecto : new ArrayList<>(efectos.keySet())) {
            if (efectos.get(nombreEfecto).esDanio()) {
                quitarEfecto(nombreEfecto);
            }
        }
    }

    /**
     * Aplica un tick de cada efecto activo de forma secuencial (compatibilidad con {@link com.rpg.core.Arena}).
     *
     * <p><strong>Nota:</strong> En el nuevo modelo de concurrencia, los efectos se procesan
     * mediante hilos {@link EfectoContinuoTask}. Este método se conserva para mantener
     * compatibilidad con {@link com.rpg.core.Arena#iniciarTurno} cuando no hay hilos activos.</p>
     *
     * @return mensajes para el registro de combate
     */
    public synchronized List<String> procesarEfectos() {
        // TALLER CONCURRENCIA: en el modelo concurrente, los efectos corren como hilos
        // EfectoContinuoTask. Este método queda como compatibilidad para el modo secuencial.
        List<String> mensajes = new ArrayList<>();
        Iterator<EstadoEfecto> iterador = efectos.values().iterator();
        while (iterador.hasNext()) {
            EstadoEfecto efecto = iterador.next();
            if (tieneHiloActivo(efecto.getNombre())) {
                continue; // lo procesa su EfectoContinuoTask: evita el doble tick
            }
            mensajes.add(aplicarTickSecuencial(efecto));
            efecto.consumirTurno();
            if (efecto.haTerminado()) {
                iterador.remove();
                detenerEfectoContinuo(efecto.getNombre());
                mensajes.add(nombre + ": termina " + efecto.getNombre());
            }
            if (!estaVivo()) {
                break;
            }
        }
        normalizarEstado();
        return mensajes;
    }

    private String aplicarTickSecuencial(EstadoEfecto efecto) {
        if (efecto.esDanio()) {
            recibirDanio(efecto.getValorPorTick());
            return String.format("%s sufre %d de dano por %s", nombre, efecto.getValorPorTick(), efecto.getNombre());
        }
        int curado = curar(efecto.getValorPorTick());
        return String.format("%s recupera %d de vida por %s", nombre, curado, efecto.getNombre());
    }

    private boolean tieneHiloActivo(String nombreEfecto) {
        if (hilosEfectos == null) return false;
        Thread hilo = hilosEfectos.get(nombreEfecto);
        return hilo != null && hilo.isAlive();
    }

    /**
     * Llamado por {@link EfectoContinuoTask} cuando su efecto termina por si solo.
     * Solo limpia si el hilo que llama sigue siendo el hilo registrado para ese efecto
     * (evita borrar un efecto del mismo nombre que fue reaplicado mientras tanto).
     */
    public synchronized void efectoTerminado(String nombreEfecto) {
        if (hilosEfectos != null && hilosEfectos.get(nombreEfecto) == Thread.currentThread()) {
            hilosEfectos.remove(nombreEfecto);
            efectos.remove(nombreEfecto);
            normalizarEstado();
        }
    }

    /** Interrumpe y elimina el hilo de efecto continuo identificado por nombre. */
    private void detenerEfectoContinuo(String nombreEfecto) {
        if (hilosEfectos == null) return;
        Thread hilo = hilosEfectos.remove(nombreEfecto);
        if (hilo != null && hilo != Thread.currentThread() && hilo.isAlive()) {
            hilo.interrupt();
        }
    }

    /** Interrumpe todos los hilos de efectos continuos activos. */
    private void detenerTodosLosEfectosContinuos() {
        if (hilosEfectos == null) return;
        for (Thread hilo : hilosEfectos.values()) {
            if (hilo != null && hilo != Thread.currentThread() && hilo.isAlive()) {
                hilo.interrupt();
            }
        }
        hilosEfectos.clear();
    }

    private boolean tieneEfectoDanio() {
        for (EstadoEfecto efecto : efectos.values()) {
            if (efecto.esDanio()) {
                return true;
            }
        }
        return false;
    }

    /** Si ya no queda ningun DoT, un personaje ENVENENADO vuelve a estar VIVO. */
    private void normalizarEstado() {
        if (estado == EstadoPersonaje.ENVENENADO && !tieneEfectoDanio()) {
            estado = EstadoPersonaje.VIVO;
        }
    }

    // ---- aturdimiento

    public synchronized void aturdir() {
        if (estaVivo()) {
            estado = EstadoPersonaje.ATURDIDO;
        }
    }

    /** @return true si estaba aturdido (y por tanto pierde este turno) */
    public synchronized boolean consumirAturdimiento() {
        boolean estabaAturdido = estado == EstadoPersonaje.ATURDIDO;
        if (estabaAturdido) {
            estado = tieneEfectoDanio() ? EstadoPersonaje.ENVENENADO : EstadoPersonaje.VIVO;
        }
        return estabaAturdido;
    }

    // ---- inventario, arma, gremio, habilidades

    public String usarPocion(Pocion pocion) {
        int curado = curar(pocion.getCuracionReal());
        if (pocion.getEfecto() != null) {
            aplicarEfecto(pocion.getEfecto());
        }
        if (pocion.isPurifica()) {
            quitarEfectosDanio();
        }
        return String.format("%s usa %s (+%d de vida)", nombre, pocion.getNombre(), curado);
    }

    public final synchronized void equiparArma(Arma nuevaArma) {
        this.arma = nuevaArma;
    }

    public synchronized void desequiparArma() {
        this.arma = null;
    }

    public void unirseAGremio(Gremio nuevoGremio) {
        if (nuevoGremio == null || nuevoGremio == gremio) {
            return;
        }
        if (gremio != null) {
            gremio.removerMiembro(this);
        }
        gremio = nuevoGremio;
        nuevoGremio.agregarMiembro(this);
    }

    public void abandonarGremio() {
        Gremio anterior = gremio;
        if (anterior == null) {
            return;
        }
        gremio = null;
        anterior.removerMiembro(this);
    }

    /** @return false si la habilidad ya estaba aprendida (el Set rechaza duplicados) */
    public final boolean aprenderHabilidad(String habilidad) {
        return habilidades.add(habilidad);
    }

    // ---- getters y setters validados

    public synchronized void setVida(int nuevaVida) {
        vida = Math.max(0, Math.min(nuevaVida, vidaMaxima));
        if (vida == 0) {
            estado = EstadoPersonaje.MUERTO;
        }
    }

    public synchronized int getNivel() {
        return nivel;
    }

    public synchronized void setNivel(int nivel) {
        if (nivel < 1) {
            throw new IllegalArgumentException("El nivel debe ser mayor o igual a 1");
        }
        this.nivel = nivel;
    }

    public void setNombre(String nombre) {
        validarNombre(nombre);
        this.nombre = nombre;
    }

    public synchronized void setVidaMaxima(int vidaMaxima) {
        if (vidaMaxima <= 0) {
            throw new IllegalArgumentException("La vida maxima debe ser positiva");
        }
        this.vidaMaxima = vidaMaxima;
        this.vida = Math.min(vida, vidaMaxima);
    }

    public int getDefensa() {
        return defensaBase + tipo.getBonificadorDefensa();
    }

    public String getNombre() {
        return nombre;
    }

    public TipoPersonaje getTipo() {
        return tipo;
    }

    public synchronized int getVida() {
        return vida;
    }

    public synchronized int getVidaMaxima() {
        return vidaMaxima;
    }

    public synchronized EstadoPersonaje getEstado() {
        return estado;
    }

    public Arma getArma() {
        return arma;
    }

    public Gremio getGremio() {
        return gremio;
    }

    public Inventario<Pocion> getInventario() {
        return inventario;
    }

    public Set<String> getHabilidades() {
        return Collections.unmodifiableSet(habilidades);
    }

    public synchronized Collection<EstadoEfecto> getEfectos() {
        return Collections.unmodifiableCollection(new ArrayList<>(efectos.values()));
    }
}
