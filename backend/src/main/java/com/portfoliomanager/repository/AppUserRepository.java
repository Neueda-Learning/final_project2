package com.portfoliomanager.repository;

import com.portfoliomanager.domain.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, String> {}
