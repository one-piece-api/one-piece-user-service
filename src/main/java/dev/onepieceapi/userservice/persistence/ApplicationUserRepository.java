package dev.onepieceapi.userservice.persistence;

import dev.onepieceapi.userservice.persistence.entity.ApplicationUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ApplicationUserRepository extends JpaRepository<ApplicationUserEntity, UUID> {

}
