package ru.tigran.personafeedbackengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.tigran.personafeedbackengine.model.Product;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByUserIdAndId(Long userId, Long productId);
    List<Product> findByUserIdAndIdIn(Long userId, List<Long> productIds);
}
