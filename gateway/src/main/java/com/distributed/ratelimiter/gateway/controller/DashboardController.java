package com.distributed.ratelimiter.gateway.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Controller serving the Rate Limiter Visualization Web Dashboard.
 */
@Controller
public class DashboardController {

    @GetMapping(value = {"/", "/index.html"}, produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public Resource getDashboard() {
        return new ClassPathResource("static/index.html");
    }
}
