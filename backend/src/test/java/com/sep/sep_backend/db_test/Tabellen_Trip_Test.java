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

import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class Tabellen_Trip_Test
{
    @Autowired
    public TripRepository repo;

    @Test
    public void testTabelleAusgabeLayout()
    {
        var EventListe = new LinkedList<Event>();
        var AusgabenListe = new LinkedList<Ausgabe>();

        var TestEntität = new Trip(EventListe, AusgabenListe);
        assertNotNull(repo);
        assertNotNull(TestEntität);
        repo.save(TestEntität);

        var EntitätenAusDb = repo.findAll();
        assertEquals(EntitätenAusDb.size(), 1);
        assertEquals(EntitätenAusDb.get(0).getAusgaben().stream().toList(), AusgabenListe);
        assertEquals(EntitätenAusDb.get(0).getEvents().stream().toList(), TestEntität.getEvents());
    }
}
