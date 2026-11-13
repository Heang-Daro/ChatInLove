package org.dr.service;

import org.dr.model.User;
import org.dr.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
@Service
@RequiredArgsConstructor
public class UserService  implements UserDetailsService{
	private final UserRepository userRepo;
	public User loadByUserId(String userId) {
		return userRepo.findById(userId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
	}
	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
	 User user =	userRepo.findByEmail(email).orElseThrow(()->new UsernameNotFoundException(email));
		return user;
	}

}
