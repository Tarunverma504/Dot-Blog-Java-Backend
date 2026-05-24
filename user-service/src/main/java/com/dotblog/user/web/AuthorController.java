package com.dotblog.user.web;

import com.dotblog.user.service.AuthorService;
import com.dotblog.user.web.dto.AuthorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2")
public class AuthorController {

    private final AuthorService authorService;

    public AuthorController(AuthorService authorService) {
        this.authorService = authorService;
    }

    @GetMapping("/Author/{id}")
    public ResponseEntity<AuthorResponse> getAuthor(@PathVariable String id) {
        AuthorResponse author = authorService.getAuthorById(id);
        return ResponseEntity.ok(author);
    }
}
