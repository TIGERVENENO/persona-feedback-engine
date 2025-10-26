package ru.tigran.personafeedbackengine.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ru.tigran.personafeedbackengine.dto.FeedbackSessionRequest;
import ru.tigran.personafeedbackengine.exception.UnauthorizedException;
import ru.tigran.personafeedbackengine.exception.ValidationException;
import ru.tigran.personafeedbackengine.model.FeedbackResult;
import ru.tigran.personafeedbackengine.model.FeedbackSession;
import ru.tigran.personafeedbackengine.model.Persona;
import ru.tigran.personafeedbackengine.model.Product;
import ru.tigran.personafeedbackengine.model.User;
import ru.tigran.personafeedbackengine.repository.FeedbackResultRepository;
import ru.tigran.personafeedbackengine.repository.FeedbackSessionRepository;
import ru.tigran.personafeedbackengine.repository.PersonaRepository;
import ru.tigran.personafeedbackengine.repository.ProductRepository;
import ru.tigran.personafeedbackengine.repository.UserRepository;
import ru.tigran.personafeedbackengine.service.FeedbackService;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class FeedbackServiceIntegrationTest {

    @Autowired
    private FeedbackService feedbackService;

    @Autowired
    private FeedbackSessionRepository feedbackSessionRepository;

    @Autowired
    private FeedbackResultRepository feedbackResultRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PersonaRepository personaRepository;

    private User testUser;
    private Product product1;
    private Product product2;
    private Persona persona1;
    private Persona persona2;

    @BeforeEach
    public void setUp() {
        // Create test user
        testUser = new User();
        testUser.setEmail("testuser@example.com");
        testUser.setPasswordHash("$2a$10$abcdefghijklmnopqrstuvwxyz");  // BCrypt hashed password
        testUser.setIsActive(true);
        testUser.setDeleted(false);
        testUser = userRepository.save(testUser);

        // Create test products
        product1 = new Product();
        product1.setName("Test Product 1");
        product1.setDescription("Description of product 1");
        product1.setUser(testUser);
        product1 = productRepository.save(product1);

        product2 = new Product();
        product2.setName("Test Product 2");
        product2.setDescription("Description of product 2");
        product2.setUser(testUser);
        product2 = productRepository.save(product2);

        // Create test personas
        persona1 = new Persona();
        persona1.setName("Test Persona 1");
        persona1.setDetailedDescription("A test persona");
        persona1.setStatus(Persona.PersonaStatus.ACTIVE);
        persona1.setUser(testUser);
        persona1 = personaRepository.save(persona1);

        persona2 = new Persona();
        persona2.setName("Test Persona 2");
        persona2.setDetailedDescription("Another test persona");
        persona2.setStatus(Persona.PersonaStatus.ACTIVE);
        persona2.setUser(testUser);
        persona2 = personaRepository.save(persona2);
    }

    @Test
    public void testStartFeedbackSessionCreatesEntities() {
        // Arrange
        FeedbackSessionRequest request = new FeedbackSessionRequest(
                Arrays.asList(product1.getId(), product2.getId()),
                Arrays.asList(persona1.getId())
        );

        // Act
        Long sessionId = feedbackService.startFeedbackSession(testUser.getId(), request);

        // Assert
        assertNotNull(sessionId);

        // Verify session was created
        FeedbackSession session = feedbackSessionRepository.findById(sessionId).orElse(null);
        assertNotNull(session);
        assertEquals(FeedbackSession.FeedbackSessionStatus.PENDING, session.getStatus());
        assertEquals(testUser.getId(), session.getUser().getId());

        // Verify feedback results were created (2 products x 1 persona = 2 results)
        List<FeedbackResult> results = feedbackResultRepository.findByFeedbackSessionId(sessionId);
        assertEquals(2, results.size());

        // Verify all results are in PENDING status
        results.forEach(result -> {
            assertEquals(FeedbackResult.FeedbackResultStatus.PENDING, result.getStatus());
            assertEquals(session.getId(), result.getFeedbackSession().getId());
        });
    }

    @Test
    public void testStartFeedbackSessionWithMultiplePersonas() {
        // Arrange
        FeedbackSessionRequest request = new FeedbackSessionRequest(
                Arrays.asList(product1.getId()),
                Arrays.asList(persona1.getId(), persona2.getId())
        );

        // Act
        Long sessionId = feedbackService.startFeedbackSession(testUser.getId(), request);

        // Assert
        FeedbackSession session = feedbackSessionRepository.findById(sessionId).orElse(null);
        assertNotNull(session);

        // Verify feedback results: 1 product x 2 personas = 2 results
        List<FeedbackResult> results = feedbackResultRepository.findByFeedbackSessionId(sessionId);
        assertEquals(2, results.size());
    }

    @Test
    public void testStartFeedbackSessionFailsWithTooManyProducts() {
        // Arrange - try to add more than 5 products
        Product product3 = new Product();
        product3.setName("Product 3");
        product3.setUser(testUser);
        product3 = productRepository.save(product3);

        Product product4 = new Product();
        product4.setName("Product 4");
        product4.setUser(testUser);
        product4 = productRepository.save(product4);

        Product product5 = new Product();
        product5.setName("Product 5");
        product5.setUser(testUser);
        product5 = productRepository.save(product5);

        Product product6 = new Product();
        product6.setName("Product 6");
        product6.setUser(testUser);
        product6 = productRepository.save(product6);

        FeedbackSessionRequest request = new FeedbackSessionRequest(
                Arrays.asList(product1.getId(), product2.getId(), product3.getId(),
                        product4.getId(), product5.getId(), product6.getId()),
                Arrays.asList(persona1.getId())
        );

        // Act & Assert
        ValidationException exception = assertThrows(ValidationException.class,
                () -> feedbackService.startFeedbackSession(testUser.getId(), request));
        assertEquals("TOO_MANY_PRODUCTS", exception.getErrorCode());
    }

    @Test
    public void testStartFeedbackSessionFailsWithUnownedProduct() {
        // Arrange - create another user and their product
        User otherUser = new User();
        otherUser.setEmail("otheruser@example.com");
        otherUser.setPasswordHash("$2a$10$abcdefghijklmnopqrstuvwxyz");  // BCrypt hashed password
        otherUser.setIsActive(true);
        otherUser.setDeleted(false);
        otherUser = userRepository.save(otherUser);

        Product otherProduct = new Product();
        otherProduct.setName("Other Product");
        otherProduct.setUser(otherUser);
        otherProduct = productRepository.save(otherProduct);

        FeedbackSessionRequest request = new FeedbackSessionRequest(
                Arrays.asList(otherProduct.getId()),
                Arrays.asList(persona1.getId())
        );

        // Act & Assert
        UnauthorizedException exception = assertThrows(UnauthorizedException.class,
                () -> feedbackService.startFeedbackSession(testUser.getId(), request));
        assertEquals("UNAUTHORIZED_ACCESS", exception.getErrorCode());
    }

    @Test
    public void testStartFeedbackSessionFailsWithUnownedPersona() {
        // Arrange - create another user and their persona
        User otherUser = new User();
        otherUser.setEmail("otheruser@example.com");
        otherUser.setPasswordHash("$2a$10$abcdefghijklmnopqrstuvwxyz");  // BCrypt hashed password
        otherUser.setIsActive(true);
        otherUser.setDeleted(false);
        otherUser = userRepository.save(otherUser);

        Persona otherPersona = new Persona();
        otherPersona.setName("Other Persona");
        otherPersona.setStatus(Persona.PersonaStatus.ACTIVE);
        otherPersona.setUser(otherUser);
        otherPersona = personaRepository.save(otherPersona);

        FeedbackSessionRequest request = new FeedbackSessionRequest(
                Arrays.asList(product1.getId()),
                Arrays.asList(otherPersona.getId())
        );

        // Act & Assert
        UnauthorizedException exception = assertThrows(UnauthorizedException.class,
                () -> feedbackService.startFeedbackSession(testUser.getId(), request));
        assertEquals("UNAUTHORIZED_ACCESS", exception.getErrorCode());
    }

    @Test
    public void testFeedbackResultsHaveCorrectAssociations() {
        // Arrange
        FeedbackSessionRequest request = new FeedbackSessionRequest(
                Arrays.asList(product1.getId(), product2.getId()),
                Arrays.asList(persona1.getId(), persona2.getId())
        );

        // Act
        Long sessionId = feedbackService.startFeedbackSession(testUser.getId(), request);

        // Assert
        List<FeedbackResult> results = feedbackResultRepository.findByFeedbackSessionId(sessionId);
        assertEquals(4, results.size()); // 2 products x 2 personas

        // Verify each result has correct associations
        results.forEach(result -> {
            assertNotNull(result.getFeedbackSession());
            assertNotNull(result.getProduct());
            assertNotNull(result.getPersona());
            assertEquals(sessionId, result.getFeedbackSession().getId());
        });

        // Verify all product-persona combinations exist
        boolean hasProduct1Persona1 = results.stream()
                .anyMatch(r -> r.getProduct().getId().equals(product1.getId()) &&
                        r.getPersona().getId().equals(persona1.getId()));
        boolean hasProduct2Persona2 = results.stream()
                .anyMatch(r -> r.getProduct().getId().equals(product2.getId()) &&
                        r.getPersona().getId().equals(persona2.getId()));

        assertTrue(hasProduct1Persona1);
        assertTrue(hasProduct2Persona2);
    }
}
