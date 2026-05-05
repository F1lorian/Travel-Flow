package com.sep.sep_backend.services;

import com.sep.sep_backend.Enitys.Ausgabe;
import com.sep.sep_backend.repos.AusgabeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AusgabeService {

    AusgabeRepository repo;

    @Autowired
    public AusgabeService(AusgabeRepository repo){
        this.repo = repo;
    }

    public void ausgabeHinzufuegen(Ausgabe ausgabe){
        repo.save(ausgabe);
    }

    public Ausgabe kostenAbfragen(){
        return null;
    }

}
