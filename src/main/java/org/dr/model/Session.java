package org.dr.model;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
@Entity
@Table(name="sessions")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Session {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private String id;
	private String deviceType;
	private String deviceName;
	@ManyToOne(targetEntity = User.class)
	@JoinColumn(name = "user_id")
	private User user;
//	location on device login like x,y
	private String mapX;
	private String mapY;
	private String city;
	private String country;
	private String sessionId;
	private String token;
	private LocalDateTime loginAt;
	private LocalDateTime updateAt;

}
