package grevcev.auth.security;

import grevcev.user.model.ReservationUserDetails;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import grevcev.user.model.User;
import grevcev.user.repository.UserRepository;

import java.util.Optional;
import java.util.List;

@Service
public class ReservationUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    public ReservationUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Optional<User> user = userRepository.findByEmail(email);
        if(!user.isPresent()) {
            throw new UsernameNotFoundException("User not found: " + email);
        }

        return new ReservationUserDetails(
                user.get().getId(),
                user.get().getEmail(),
                user.get().getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_"+user.get().getRole().name()))
        );
    }


}
