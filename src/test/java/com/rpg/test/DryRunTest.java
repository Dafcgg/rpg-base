package com.rpg.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Ejecuta la suite heredada sin tocar las partidas ni el historial del usuario. */
class DryRunTest {
    @TempDir Path directorio;

    @Test
    void legacyDryRunRunsInAnIsolatedDirectory() throws Exception {
        Path fuentes = Path.of("src").toAbsolutePath();
        try (var rutas = Files.walk(fuentes)) {
            for (Path origen : rutas.toList()) {
                Path destino = directorio.resolve("src").resolve(fuentes.relativize(origen));
                if (Files.isDirectory(origen)) {
                    Files.createDirectories(destino);
                } else {
                    Files.copy(origen, destino);
                }
            }
        }
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Path log = directorio.resolve("dry-run.log");
        Process proceso = new ProcessBuilder(java, "-cp", classpath, DryRunSuite.class.getName())
                .directory(directorio.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean termino = proceso.waitFor(55, TimeUnit.SECONDS);
        if (!termino) {
            proceso.destroyForcibly();
        }
        String salida = Files.readString(log);
        assertTrue(termino, () -> "Dry run bloqueado:\n" + salida);
        assertEquals(0, proceso.exitValue(), () -> salida);
        assertTrue(salida.contains("0 fallidas"), () -> salida);
        salida.lines().filter(linea -> linea.contains("RESULTADO GLOBAL")).forEach(System.out::println);
    }
}
