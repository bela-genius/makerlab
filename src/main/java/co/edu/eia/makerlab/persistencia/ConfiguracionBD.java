package co.edu.eia.makerlab.persistencia;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import co.edu.eia.makerlab.excepcion.MakerLabException;

/**
 * Lee los datos de conexión del archivo .env de la raíz del proyecto, para
 * no escribir usuario y contraseña dentro del código. Formato:
 *
 *   DB_URL=jdbc:postgresql://localhost:5432/makerlab_poo
 *   DB_USER=postgres
 *   DB_PASSWORD=la_clave
 *
 * Properties ya sabe leer líneas CLAVE=VALOR, así que no hace falta otra librería.
 */
public class ConfiguracionBD {

    private String url;
    private String usuario;
    private String clave;

    public ConfiguracionBD(String url, String usuario, String clave) {
        this.url = url;
        this.usuario = usuario;
        this.clave = clave;
    }

    public static ConfiguracionBD desdeArchivo(Path archivo) throws MakerLabException {
        if (!Files.exists(archivo)) {
            throw new MakerLabException("No existe el archivo " + archivo.toAbsolutePath());
        }
        Properties datos = new Properties();
        try (FileReader lector = new FileReader(archivo.toFile())) {
            datos.load(lector);
        } catch (IOException e) {
            throw new MakerLabException("No se pudo leer " + archivo + ": " + e.getMessage());
        }
        String url = datos.getProperty("DB_URL");
        String usuario = datos.getProperty("DB_USER");
        String clave = datos.getProperty("DB_PASSWORD", "");
        if (url == null || url.isBlank() || usuario == null || usuario.isBlank()) {
            throw new MakerLabException("El archivo " + archivo + " debe tener DB_URL y DB_USER.");
        }
        return new ConfiguracionBD(url.trim(), usuario.trim(), clave.trim());
    }

    public String getUrl() {
        return url;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getClave() {
        return clave;
    }
}
