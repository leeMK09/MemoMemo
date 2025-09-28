package com.playground.elasticsearchproduct.product;

import com.playground.elasticsearchproduct.product.domain.ProductDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ProductDocumentRepository extends ElasticsearchRepository<ProductDocument, String> {
}
