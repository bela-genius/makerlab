package co.edu.eia.makerlab.app;

import java.util.Scanner;

import co.edu.eia.makerlab.excepcion.MakerLabException;

/** Versión de consola. La interfaz gráfica se abre con vista.Lanzador. */
public class Main {

    public static void main(String[] args) {
        Scanner teclado = new Scanner(System.in);
        try {
            System.out.println(Arranque.iniciar());
            new MenuConsola(teclado).iniciar();
        } catch (MakerLabException e) {
            System.out.println("Error en los datos de demostracion: " + e.getMessage());
        } finally {
            // Se cierra el teclado pase lo que pase.
            teclado.close();
        }
    }
}
