package com.example.securechat.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.securechat.entity.User;

public interface UserRepository extends JpaRepository<User, UUID> {
}
