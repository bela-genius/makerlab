package co.edu.eia.makerlab.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import co.edu.eia.makerlab.excepcion.MakerLabException;

/** Lectura del archivo .env. No necesita una base de datos real. */
class ConfiguracionBDTest {

    @TempDir
    Path carpeta; // JUnit crea una carpeta temporal para cada prueba

    @Test
    void leeLosTresDatosDeConexion() throws IOException, MakerLabException {
        Path env = carpeta.resolve(".env");
        Files.writeString(env, "DB_URL=jdbc:postgresql://localhost:5432/makerlab_poo\nDB_USER=postgres\nDB_PASSWORD=secreta\n");

        ConfiguracionBD config = ConfiguracionBD.desdeArchivo(env);

        assertEquals("jdbc:postgresql://localhost:5432/makerlab_poo", config.getUrl());
        assertEquals("postgres", config.getUsuario());
        assertEquals("secreta", config.getClave());
    }

    @Test
    void sinArchivoLanzaExcepcion() {
        assertThrows(MakerLabException.class, () -> ConfiguracionBD.desdeArchivo(carpeta.resolve("no-existe.env")));
    }

    @Test
    void sinUsuarioLanzaExcepcion() throws IOException {
        Path env = carpeta.resolve(".env");
        Files.writeString(env, "DB_URL=jdbc:postgresql://localhost:5432/makerlab_poo\n");
        assertThrows(MakerLabException.class, () -> ConfiguracionBD.desdeArchivo(env));
    }
}
