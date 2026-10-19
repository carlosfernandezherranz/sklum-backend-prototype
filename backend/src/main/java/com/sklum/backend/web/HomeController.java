package com.sklum.backend.web;

import com.sklum.backend.model.HomeResponse;
import com.sklum.backend.service.HomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Home", description = "Minimal home-screen aggregation (my-deliveries widget only)")
public class HomeController {

    private final HomeService homeService;

    public HomeController(HomeService homeService) {
        this.homeService = homeService;
    }

    @Operation(summary = "Home screen aggregation — \"my deliveries\" widget only (no auth in this prototype)")
    @GetMapping("/home")
    public HomeResponse home() {
        return homeService.home();
    }
}
