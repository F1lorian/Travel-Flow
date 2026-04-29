package com.sep.sep_backend.db_test;
import com.sep.sep_backend.Enitys.Event;
import com.sep.sep_backend.repos.EventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class Tabelle_Event_Test {

    @Autowired
    public EventRepository repo;
    @Autowired
    private DataSource dataSource;

    @Test
    public void TestAnfrageEvent()
    {
        var TestEntität = new Event(20, null);
        assertNotNull(repo);
        assertNotNull(TestEntität);
        repo.save(TestEntität);

        var EntitätenAusDb = repo.findAll();
        assertEquals(EntitätenAusDb.size(), 1);
        assertEquals(EntitätenAusDb.get(0).getKosten(), TestEntität.getKosten());
        assertEquals(EntitätenAusDb.get(0).getTrip(), TestEntität.getTrip());
    }

   //Todo: Test erstellen der Dass Schema an sich überprüft
}
