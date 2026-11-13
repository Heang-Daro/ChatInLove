package org.dr.repository;

import java.util.Optional;

import org.dr.model.Room;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, Long> {
	Optional<Room> findByRoomId(String roomId);
	

}
