package com.dim.service;

import com.dim.entity.User;
import com.dim.repository.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;


import java.util.List;

import static io.quarkus.arc.ComponentsProvider.LOG;

@ApplicationScoped
public class UserService {
    @Inject
    UserRepository userRepository;

    public List<User> findAll() {
        return userRepository.listAll();
    }

    @Transactional
    public void addUser(String name, String email ) {
        try{

            User newUser = new User();
            newUser.name = name;
            newUser.email = email;
            userRepository.persist(newUser);
            LOG.info("Utilisateur ajouté : " + name + " (" + email + ")");

        }
        catch (Exception e){
            LOG.error("Erreur lors de l'ajout de l'utilisateur : " + name, e);
            throw e; // ou gérer l'exception selon ton besoin
        }


    }
}


