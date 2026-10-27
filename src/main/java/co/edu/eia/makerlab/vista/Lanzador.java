package co.edu.eia.makerlab.vista;

import javafx.application.Application;

/**
 * Punto de entrada de la interfaz gráfica.
 *
 * Si se ejecuta directamente AppFX (que hereda de Application) desde
 * Eclipse sin configurar módulos, Java muestra "JavaFX runtime components
 * are missing". Lanzar desde una clase normal evita ese problema.
 */
public class Lanzador {

    public static void main(String[] args) {
        Application.launch(AppFX.class, args);
    }
}
