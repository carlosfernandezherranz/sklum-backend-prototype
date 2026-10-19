package com.sklum.backend.service;

import com.sklum.backend.model.HomeResponse;
import com.sklum.backend.repository.OrderRepository;
import org.springframework.stereotype.Service;

@Service
public class HomeService {

    private final OrderRepository orderRepository;

    public HomeService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /** Minimal "my deliveries" widget — just the most recent orders, no per-customer auth in this prototype. */
    public HomeResponse home() {
        return new HomeResponse(orderRepository.recentOrders(5));
    }
}
