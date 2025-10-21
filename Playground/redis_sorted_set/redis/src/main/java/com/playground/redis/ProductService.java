package com.playground.redis;

import com.playground.redis.domain.Order;
import com.playground.redis.domain.Product;
import com.playground.redis.domain.ProductDto;
import com.playground.redis.repository.OrderRepository;
import com.playground.redis.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final RedisTemplate<String, ProductDto> rankTemplate;

    public void purchase(Long id) {
        Product product = productRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        ZSetOperations<String, ProductDto> rankOps = rankTemplate.opsForZSet();
        orderRepository.save(new Order(product));

        rankOps.incrementScore("soldRanks", ProductDto.from(product), 1);
    }

    public List<ProductDto> getMostSold() {
        Set<ProductDto> ranks = rankTemplate.opsForZSet().reverseRange("soldRanks", 0, 9);

        if (ranks == null) {
            return Collections.emptyList();
        }

        return ranks.stream().toList();
    }
}
