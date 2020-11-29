package com.quarkus.demo;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.ClassRule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@Testcontainers
class UserServiceTest {

    @ClassRule
    @SuppressWarnings("rawtypes")
    public static GenericContainer postgres = new GenericContainer("postgres:latest")
            .withEnv("POSTGRES_PASSWORD", "postgres")
            .withEnv("POSTGRES_USER", "postgres");

    @Inject
    EntityManager entityManager;

    @Inject
    UserService userService;

    @Inject
    UserRepository userRepository;

    private User userA;
    private User userB;


    @BeforeEach
    @Transactional
    void setUp() {
        cleanFollowerTable();
        userA = userRepository.findById(1L);
        userB = userRepository.findById(2L);
    }


    @Test
    void testNoFollowersExist() {
        userService.follow(userA.id, userB.id);
        assertEquals(0, userA.getFollowing().size()); //success
    }

    /**
     *
     */
    @Test
    void testAddFollowerCollectionContainsReference() {
        userService.follow(userA.id, userB.id);
        userA = userRepository.findById(userA.id); //reload
        assertEquals(1, userA.getFollowing().size()); //success
    }

    /**
     * Should succeed
     */
    @Test
    void testAddFollowerDatabaseContainsEntry() {
        userService.follow(userA.id, userB.id);
        userA = userRepository.findById(userA.id); //reload
        assertEquals(1, userService.getFollowingCount(userA.id)); //success
    }

    /**
     * Fails with
     * Unable to perform requested lazy initialization [com.quarkus.demo.User.following] - no session and settings disallow loading outside the Session
     * org.hibernate.LazyInitializationException: Unable to perform requested lazy initialization [com.quarkus.demo.User.following] - no session and settings disallow loading outside the
     */
    @Test
    void testRemoveFollowing() {
        userService.follow(userA.id, userB.id);
        assertEquals(1, userA.getFollowing().size()); //success
        assertEquals(1, userService.getFollowingCount(userA.id));//fail

        userService.unfollow(userA.id, userB.id);

        userA = userRepository.findById(1L);

        assertEquals(0, userService.getFollowingCount(userA.id)); //select call - succeeds
        assertEquals(0, userA.getFollowing().size()); // fails
    }


    /**
     * Succeeds
     */
    @Test
    void testRemoveFollowingWithReloadBeforeAccessingCollection() {
        userService.follow(userA.id, userB.id);

        /*
         * Why do I need to reload here in order to access the collection with getFollowing?
         */
        userA = userRepository.findById(userA.id);

        assertEquals(1, userA.getFollowing().size());
        assertEquals(1, userService.getFollowingCount(userA.id));

        userService.unfollow(userA.id, userB.id);

        userA = userRepository.findById(1L);

        assertEquals(0, userService.getFollowingCount(userA.id)); //select call - succeeds
        assertEquals(0, userA.getFollowing().size()); // fails
    }


    @AfterEach
    @Transactional
    void tearDown() {
        cleanFollowerTable();
    }

    @SuppressWarnings("SqlWithoutWhere")
    private void cleanFollowerTable() {
        entityManager
                .createNativeQuery("delete from followers")
                .executeUpdate();
    }
}