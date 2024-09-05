package com.WebScraper.Culin.controller;

import com.WebScraper.Culin.service.UserDetailsServiceImpl;
import com.WebScraper.Culin.service.UserService;
import com.WebScraper.Culin.user.User;
import com.WebScraper.Culin.utils.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@Slf4j
public class AuthController {

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private AuthenticationManager authenticationManager;

//    @PostMapping("/signup")
//    public void signUp(@RequestBody User user){
//        userService.saveNewUser(user);
//    }

    @CrossOrigin(origins = "*")
    @PostMapping("/signup")
    public ResponseEntity signUp(@RequestBody User user) {
        System.out.println("SIGNUP USER: " + user);
        userService.saveNewUser(user);
        return new ResponseEntity(HttpStatus.OK);
    }

//    @PostMapping("/login")
//    public ResponseEntity<String> login(@RequestBody User user){
//        try{
//            authenticationManager.authenticate(
//                    new UsernamePasswordAuthenticationToken(user.getUsername(), user.getPassword())
//            );
//            UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
//            String jwt = jwtUtils.generateToken(userDetails.getUsername());
//            return new ResponseEntity<>(jwt, HttpStatus.OK);
//        }catch (Exception e){
//            log.error("Exception occurred while createAuthenticationToken: ", e);
//            return new ResponseEntity<>("Incorrect username or password!", HttpStatus.BAD_REQUEST);
//        }
//    }

    @CrossOrigin(origins = "*")
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody User user) {
        System.out.println("LOGIN USER: " + user);
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getUsername(), user.getPassword())
            );
            UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
            String jwt = jwtUtils.generateToken(userDetails.getUsername());
            Map<String, String> response = new HashMap<>();
            response.put("jwt", jwt);
            System.out.println("JWT: " + jwt);
            System.out.println("RESPONSE: " + response);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            log.error("Exception occurred while createAuthenticationToken: ", e);
            return new ResponseEntity<>(Map.of("message", "Incorrect username or password!"), HttpStatus.BAD_REQUEST);
        }
    }


}
