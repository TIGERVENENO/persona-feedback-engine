package ru.tigran.personafeedbackengine.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import ru.tigran.personafeedbackengine.dto.ProductRequest;
import ru.tigran.personafeedbackengine.dto.ProductResponse;
import ru.tigran.personafeedbackengine.exception.UnauthorizedException;
import ru.tigran.personafeedbackengine.service.ProductService;

import java.util.List;
import java.util.Optional;

/**
 * REST API для управления продуктами.
 * Требует JWT авторизации для всех эндпоинтов.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products", description = "Управление продуктами для получения фидбека")
@SecurityRequirement(name = "bearer-jwt")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * Безопасно извлекает userId из SecurityContext.
     * Выкидывает UnauthorizedException если токен отсутствует или невалиден.
     *
     * @return User ID из JWT токена
     * @throws UnauthorizedException если аутентификация отсутствует
     */
    private Long extractAuthenticatedUserId() {
        return Optional
                .ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(auth -> (Long) auth.getPrincipal())
                .orElseThrow(() -> new UnauthorizedException(
                        "Missing or invalid JWT token in Authorization header",
                        "MISSING_AUTHENTICATION"
                ));
    }

    /**
     * Создать новый продукт.
     *
     * @param request данные продукта (name, description)
     * @return ProductResponse с ID созданного продукта
     */
    @PostMapping
    @Operation(
            summary = "Создать продукт",
            description = "Создаёт новый продукт для текущего пользователя. " +
                    "Продукт можно использовать для получения AI-фидбека от персон."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Продукт успешно создан",
                    content = @Content(schema = @Schema(implementation = ProductResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Неверные параметры запроса (пустое имя или слишком длинное описание)"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Отсутствует JWT токен в заголовке Authorization"
            )
    })
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody ProductRequest request
    ) {
        Long userId = extractAuthenticatedUserId();
        log.info("POST /api/v1/products - user: {}, product name: {}", userId, request.name());

        ProductResponse response = productService.createProduct(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Получить все продукты текущего пользователя.
     *
     * @return список продуктов
     */
    @GetMapping
    @Operation(
            summary = "Получить все продукты",
            description = "Возвращает все продукты текущего пользователя (кроме удалённых)."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Список продуктов успешно получен"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Отсутствует JWT токен в заголовке Authorization"
            )
    })
    public ResponseEntity<List<ProductResponse>> getAllProducts() {
        Long userId = extractAuthenticatedUserId();
        log.info("GET /api/v1/products - user: {}", userId);

        List<ProductResponse> products = productService.getAllProducts(userId);

        return ResponseEntity.ok(products);
    }

    /**
     * Получить продукт по ID.
     *
     * @param productId ID продукта
     * @return ProductResponse
     */
    @GetMapping("/{productId}")
    @Operation(
            summary = "Получить продукт по ID",
            description = "Возвращает данные продукта по его ID. Проверяется ownership пользователя."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Продукт успешно получен",
                    content = @Content(schema = @Schema(implementation = ProductResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Продукт не найден или не принадлежит текущему пользователю"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Отсутствует JWT токен в заголовке Authorization"
            )
    })
    public ResponseEntity<ProductResponse> getProduct(@PathVariable Long productId) {
        Long userId = extractAuthenticatedUserId();
        log.info("GET /api/v1/products/{} - user: {}", productId, userId);

        ProductResponse response = productService.getProduct(userId, productId);

        return ResponseEntity.ok(response);
    }

    /**
     * Обновить существующий продукт.
     *
     * @param productId ID продукта
     * @param request новые данные продукта
     * @return обновлённый ProductResponse
     */
    @PutMapping("/{productId}")
    @Operation(
            summary = "Обновить продукт",
            description = "Обновляет данные существующего продукта. Проверяется ownership пользователя."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Продукт успешно обновлён",
                    content = @Content(schema = @Schema(implementation = ProductResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Неверные параметры запроса"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Продукт не найден или не принадлежит текущему пользователю"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Отсутствует JWT токен в заголовке Authorization"
            )
    })
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long productId,
            @Valid @RequestBody ProductRequest request
    ) {
        Long userId = extractAuthenticatedUserId();
        log.info("PUT /api/v1/products/{} - user: {}, new name: {}", productId, userId, request.name());

        ProductResponse response = productService.updateProduct(userId, productId, request);

        return ResponseEntity.ok(response);
    }

    /**
     * Удалить продукт (soft delete).
     *
     * @param productId ID продукта
     * @return 204 No Content
     */
    @DeleteMapping("/{productId}")
    @Operation(
            summary = "Удалить продукт",
            description = "Помечает продукт как удалённый (soft delete). " +
                    "Продукт остаётся в базе данных для сохранения истории фидбека."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "Продукт успешно удалён"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Продукт не найден или не принадлежит текущему пользователю"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Отсутствует JWT токен в заголовке Authorization"
            )
    })
    public ResponseEntity<Void> deleteProduct(@PathVariable Long productId) {
        Long userId = extractAuthenticatedUserId();
        log.info("DELETE /api/v1/products/{} - user: {}", productId, userId);

        productService.deleteProduct(userId, productId);

        return ResponseEntity.noContent().build();
    }
}
