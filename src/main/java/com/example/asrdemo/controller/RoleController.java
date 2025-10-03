package com.example.asrdemo.controller;

import com.example.asrdemo.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/role")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    public List<Map<String,Object>> all() {
        return roleService.all();
    }

    @GetMapping("/{id}")
    public Map<String,Object> one(@PathVariable long id) {
        return roleService.findById(id);
    }
}
