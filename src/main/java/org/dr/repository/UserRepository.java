package org.dr.repository;

import org.dr.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByEmailAndIdNot(String email, String id);
    List<User> findByUsernameContainingIgnoreCase(String username);

    @Modifying
    @Query("UPDATE User u SET u.profileImageUrl = :url, u.updatedAt = CURRENT_TIMESTAMP WHERE u.id = :id")
    void updateProfileImageUrl(@Param("id") String id, @Param("url") String url);

    @Modifying
    @Query("UPDATE User u SET u.foregroundImageUrl = :url, u.updatedAt = CURRENT_TIMESTAMP WHERE u.id = :id")
    void updateForegroundImageUrl(@Param("id") String id, @Param("url") String url);
}
