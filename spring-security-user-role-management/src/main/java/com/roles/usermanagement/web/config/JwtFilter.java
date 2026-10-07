package com.roles.usermanagement.web.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtFilter extends OncePerRequestFilter {

 private final JwtUtil jwt;
 private final UserDetailsService users;

 public JwtFilter(JwtUtil jwt, UserDetailsService users) {
  this.jwt = jwt;
  this.users = users;
 }

 @Override
 protected void doFilterInternal(
         HttpServletRequest request,
         HttpServletResponse response,
         FilterChain chain
 ) throws ServletException, IOException {
  String header = request.getHeader(HttpHeaders.AUTHORIZATION);

  if (header != null && header.startsWith("Bearer ")) {
   String token = header.substring(7).trim();

   if (!token.isEmpty() && jwt.isValid(token)) {
    try {
     UserDetails user = users.loadUserByUsername(jwt.getUsername(token));

     // El token debe haberse emitido con la contraseña actual (si cambió, el token ya no sirve).
     if (jwt.matchesPassword(token, user.getPassword())
             && user.isEnabled()
             && user.isAccountNonLocked()
             && user.isAccountNonExpired()
             && user.isCredentialsNonExpired()) {
      var authentication = new UsernamePasswordAuthenticationToken(
              user.getUsername(),
              null,
              user.getAuthorities()
      );
      authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
      SecurityContextHolder.getContext().setAuthentication(authentication);
     }
    } catch (UsernameNotFoundException ignored) {
     SecurityContextHolder.clearContext();
    }
   }
  }

  chain.doFilter(request, response);
 }
}