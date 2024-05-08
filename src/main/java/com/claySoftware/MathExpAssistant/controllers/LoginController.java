package com.claySoftware.MathExpAssistant.controllers;


import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoginController {

    @RequestMapping(path = "/login", method = RequestMethod.POST)
    public ResponseEntity<String> loginEnpoint(String user, String password) {
       return ResponseEntity.ok("Test");
    };

    @RequestMapping(path = "/", method = RequestMethod.GET)
    public ResponseEntity<String> helloEndpoint() {
        return ResponseEntity.ok("Hello world");
    };

}
