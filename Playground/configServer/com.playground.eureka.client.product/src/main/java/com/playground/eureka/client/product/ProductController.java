package com.playground.eureka.client.product;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RefreshScope
@RestController
public class ProductController {

    @Value("${server.port}")
    private String port;

    @Value("${message}")
    private String message;

    @GetMapping("/product")
    public String getProduct(){
        return "Product info from port : " + port + " message: " + message;
    }
}
