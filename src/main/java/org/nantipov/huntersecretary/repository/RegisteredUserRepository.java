package org.nantipov.huntersecretary.repository;

import org.nantipov.huntersecretary.domain.entiry.RegisteredUser;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface RegisteredUserRepository extends CrudRepository<RegisteredUser, Long> {
    Optional<RegisteredUser> findByEmailAddress(String emailAddress);
}
