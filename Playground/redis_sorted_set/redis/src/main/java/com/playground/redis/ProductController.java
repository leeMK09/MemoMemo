package com.playground.redis;

import com.playground.redis.domain.ProductDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;

    @PostMapping("/{id}/purchase")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void purchase(@PathVariable("id") Long id) {
        productService.purchase(id);
    }

    @GetMapping("/ranks")
    public List<ProductDto> getRanks() {
        return productService.getMostSold();
    }
}
