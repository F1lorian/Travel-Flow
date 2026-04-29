package com.sep.sep_backend.db_test;
import com.sep.sep_backend.Enitys.Ausgabe;
import com.sep.sep_backend.Enitys.Event;
import com.sep.sep_backend.Enitys.Trip;
import com.sep.sep_backend.repos.AusgabeRepository;
import com.sep.sep_backend.repos.TripRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

//AutoConfigureTestDatabase ist nur Üergangslösung,
//Notiz: Docker anpassen test db in Container packen und anpassen dass Tests darauf gehen
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class Tabelle_Ausgabe_Test
{
    @Autowired
    public AusgabeRepository repo;

    @Autowired
    public TripRepository repoTrip;

    @Test
    public void testTabelleAusgabeLayout()
    {
        var Trip = new Trip();
        var TestEntität = new Ausgabe(20, Trip);
        assertNotNull(repo);
        assertNotNull(repoTrip);
        assertNotNull(TestEntität);
        assertNotNull(Trip);
        repoTrip.save(Trip);
        repo.save(TestEntität);

        var EntitätenAusDb = repo.findAll();
        assertEquals(EntitätenAusDb.size(), 1);
        assertEquals(EntitätenAusDb.get(0).getKosten(), TestEntität.getKosten());
        assertEquals(EntitätenAusDb.get(0).getTrip(), TestEntität.getTrip());
    }
}
