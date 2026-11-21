package ar.turnotarde.soporte;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Clock mutable para tests: se puede fijar en un instante y avanzar. */
public class RelojDePrueba extends Clock {

    private final ZoneId zona;
    private volatile Instant instante;

    public RelojDePrueba(Instant inicial) {
        this(inicial, ZoneOffset.UTC);
    }

    private RelojDePrueba(Instant inicial, ZoneId zona) {
        this.instante = inicial;
        this.zona = zona;
    }

    public void fijar(Instant nuevo) {
        this.instante = nuevo;
    }

    public void avanzar(Duration duracion) {
        this.instante = this.instante.plus(duracion);
    }

    @Override
    public ZoneId getZone() {
        return zona;
    }

    @Override
    public Clock withZone(ZoneId otraZona) {
        return new RelojDePrueba(instante, otraZona);
    }

    @Override
    public Instant instant() {
        return instante;
    }
}
