package org.dr.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.dr.dto.response.UserResponse;
import org.dr.model.User;
import org.dr.repository.UserRepository;
import org.dr.service.AuthenticationService;
import org.dr.service.DeviceSessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@CrossOrigin("*")
@RequiredArgsConstructor
public class PublicUserController {
	private final UserRepository repository;
	
	@GetMapping("/search{username}")
	public ResponseEntity<?> findUserByUsername(@PathVariable String username){
		List<User> users = repository.findByUsernameContainingIgnoreCase(username);
		
		return ResponseEntity.ok(users.stream()
				.map(user-> UserResponse.builder()
						.id(user.getId())
						.username(user.getUsername())
						.email(user.getEmail()).build()).collect(Collectors.toList()));
		
	}

}
