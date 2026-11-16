package org.dr.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name="user_activities")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserActivity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	private boolean isOnline;
	@JoinColumn(name = "user_id")
	@OneToOne
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	private User user;

}
