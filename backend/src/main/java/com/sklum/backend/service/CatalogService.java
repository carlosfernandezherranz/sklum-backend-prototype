package com.sklum.backend.service;

import com.sklum.backend.model.PdpResponse;
import com.sklum.backend.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
public class CatalogService {

    private final ProductRepository productRepository;
    private final DeliveryPromiseService deliveryPromiseService;

    public CatalogService(ProductRepository productRepository, DeliveryPromiseService deliveryPromiseService) {
        this.productRepository = productRepository;
        this.deliveryPromiseService = deliveryPromiseService;
    }

    public PdpResponse pdp(String sku, String postalCodeOrNull) {
        var product = productRepository.findBySku(sku)
                .orElseThrow(() -> new NoSuchElementException("no such product: " + sku));
        var promise = deliveryPromiseService.pdpPromise(sku, postalCodeOrNull);
        return new PdpResponse(product, promise);
    }
}
