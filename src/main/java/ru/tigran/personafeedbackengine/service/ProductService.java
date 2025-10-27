package ru.tigran.personafeedbackengine.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.tigran.personafeedbackengine.dto.ProductRequest;
import ru.tigran.personafeedbackengine.dto.ProductResponse;
import ru.tigran.personafeedbackengine.exception.ErrorCode;
import ru.tigran.personafeedbackengine.exception.ValidationException;
import ru.tigran.personafeedbackengine.model.Product;
import ru.tigran.personafeedbackengine.model.User;
import ru.tigran.personafeedbackengine.repository.ProductRepository;
import ru.tigran.personafeedbackengine.repository.UserRepository;

import java.util.List;

/**
 * Сервис для управления продуктами.
 * Выполняет CRUD операции с проверкой ownership пользователя.
 */
@Slf4j
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public ProductService(ProductRepository productRepository, UserRepository userRepository) {
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    /**
     * Создаёт новый продукт для пользователя.
     *
     * @param userId ID пользователя из JWT
     * @param request данные продукта
     * @return ProductResponse с ID и данными созданного продукта
     */
    @Transactional
    public ProductResponse createProduct(Long userId, ProductRequest request) {
        log.info("Creating product for user {}: {}", userId, request.name());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ValidationException(
                        "User not found",
                        ErrorCode.ENTITY_NOT_FOUND.getCode()
                ));

        Product product = new Product();
        product.setName(request.name());
        product.setDescription(request.description());
        product.setUser(user);
        product.setDeleted(false);

        Product saved = productRepository.save(product);
        log.info("Product created with id: {}", saved.getId());

        return mapToResponse(saved);
    }

    /**
     * Получает продукт по ID с проверкой ownership.
     *
     * @param userId ID пользователя из JWT
     * @param productId ID продукта
     * @return ProductResponse
     */
    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long userId, Long productId) {
        log.debug("Getting product {} for user {}", productId, userId);

        Product product = productRepository.findByUserIdAndId(userId, productId)
                .orElseThrow(() -> new ValidationException(
                        "Product not found or access denied",
                        ErrorCode.ENTITY_NOT_FOUND.getCode()
                ));

        if (Boolean.TRUE.equals(product.getDeleted())) {
            throw new ValidationException(
                    "Product has been deleted",
                    ErrorCode.ENTITY_NOT_FOUND.getCode()
            );
        }

        return mapToResponse(product);
    }

    /**
     * Получает все продукты пользователя (не помеченные как deleted).
     *
     * @param userId ID пользователя из JWT
     * @return список ProductResponse
     */
    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProducts(Long userId) {
        log.debug("Getting all products for user {}", userId);

        return productRepository.findAll().stream()
                .filter(p -> p.getUser().getId().equals(userId))
                .filter(p -> !Boolean.TRUE.equals(p.getDeleted()))
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Обновляет существующий продукт.
     *
     * @param userId ID пользователя из JWT
     * @param productId ID продукта
     * @param request новые данные
     * @return ProductResponse с обновлёнными данными
     */
    @Transactional
    public ProductResponse updateProduct(Long userId, Long productId, ProductRequest request) {
        log.info("Updating product {} for user {}", productId, userId);

        Product product = productRepository.findByUserIdAndId(userId, productId)
                .orElseThrow(() -> new ValidationException(
                        "Product not found or access denied",
                        ErrorCode.ENTITY_NOT_FOUND.getCode()
                ));

        if (Boolean.TRUE.equals(product.getDeleted())) {
            throw new ValidationException(
                    "Cannot update deleted product",
                    ErrorCode.VALIDATION_ERROR.getCode()
            );
        }

        product.setName(request.name());
        product.setDescription(request.description());

        Product saved = productRepository.save(product);
        log.info("Product {} updated successfully", productId);

        return mapToResponse(saved);
    }

    /**
     * Soft delete продукта.
     * Помечает продукт как deleted, но не удаляет из БД (сохраняет feedback историю).
     *
     * @param userId ID пользователя из JWT
     * @param productId ID продукта
     */
    @Transactional
    public void deleteProduct(Long userId, Long productId) {
        log.info("Deleting product {} for user {}", productId, userId);

        Product product = productRepository.findByUserIdAndId(userId, productId)
                .orElseThrow(() -> new ValidationException(
                        "Product not found or access denied",
                        ErrorCode.ENTITY_NOT_FOUND.getCode()
                ));

        product.setDeleted(true);
        productRepository.save(product);

        log.info("Product {} marked as deleted", productId);
    }

    /**
     * Маппинг Product entity → ProductResponse DTO.
     */
    private ProductResponse mapToResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription()
        );
    }
}
