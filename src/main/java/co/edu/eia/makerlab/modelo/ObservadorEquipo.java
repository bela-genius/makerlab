package co.edu.eia.makerlab.modelo;

/**
 * Patrón Observer: quien implemente esta interfaz se entera cada vez que
 * un equipo cambia de estado.
 *
 * Está en modelo (y no en patron) porque Equipo la usa. Si estuviera en
 * patron, modelo dependería de patron y patron de modelo.
 */
public interface ObservadorEquipo {

    void estadoCambiado(Equipo equipo);
}
