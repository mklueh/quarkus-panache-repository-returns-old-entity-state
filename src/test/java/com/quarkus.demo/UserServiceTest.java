package com.quarkus.demo;

import io.quarkus.test.junit.QuarkusTest;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.junit.ClassRule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Attempts to make accessing the many-to-many access working
 *
 * Topics:
 * 1. Lazy collection not loaded (LazyInitializationException)
 * 2. PanacheRepository returns old entity state
 */
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


    /**
     * First PersistenceContext. When this method has finished,
     * all loaded entities are in the detached state
     */
    @BeforeEach
    @Transactional
    void setUp() {
        cleanFollowerTable();
        userA = userRepository.findById(1L);
        userB = userRepository.findById(2L);
    }


    /**
     * LazyInitializationException
     */
    @Test
    void testNoFollowersExist() {
        assertEquals(0, userA.getFollowing().size()); //success
    }


    @Test
    @Transactional
    void testNoFollowersExistByCollection() {
        userA = userRepository.findById(userA.id);
        assertEquals(0, userA.getFollowing().size()); //success
    }


    /**
     * LazyInitializationException
     */
    @Test
    @Transactional
    void testNoFollowersExistByCollectionHibernateInitialize() {
        Hibernate.initialize(userA.getFollowers());
        assertEquals(0, userA.getFollowing().size()); //fails
    }

    /**
     * Not supported for JTA entity managers
     */
    @Test
    void testNoFollowersExistWithEMTx() {
        entityManager.getTransaction().begin();
        assertEquals(0, userA.getFollowing().size()); //fails
        entityManager.getTransaction().commit();
    }

    /**
     * Entity not managed
     * Entity is "detached" as the new Session of this test
     * does not contain the entity
     */
    @Test
    @Transactional
    void testNoFollowersExistWithSession() {
        entityManager.unwrap(Session.class).refresh(userA);
        assertEquals(0, userA.getFollowing().size()); //fails
    }

    /**
     * Entity not managed
     * Entity is "reattached" before
     * https://stackoverflow.com/questions/912659/what-is-the-proper-way-to-re-attach-detached-objects-in-hibernate
     */
    @Test
    @Transactional
    void testNoFollowersExistMergeReattach() {
        entityManager.merge(userA);
        entityManager.unwrap(Session.class).refresh(userA);
        assertEquals(0, userA.getFollowing().size()); //fails
    }

    /**
     * Entity not managed
     * Entity is "detached" as the new Session of this test
     * does not contain the entity
     */
    @Test
    @Transactional
    void testNoFollowersExistEntityManagerRefresh() {
        entityManager.refresh(userA);
        assertEquals(0, userA.getFollowing().size()); //fails
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
     * PanacheRepository returns old entity state
     * The last two assertEquals show the main issue. While the userService.getFollowingCount call executes a select to the database
     * and shows the right count, the userA.getFollowing().size() call still contains the old follower reference,
     * although it has been removed again by the userService.unfollow() call.
     *
     * It seems that the repository uses some kind of cache that is not invalidated
     */
    @Test
    void testRemoveFollowingWithReloadBeforeAccessingCollection() {
        userService.follow(userA.id, userB.id);

        /*
         * Reload to prevent LazyInitializationException
         */
        userA = userRepository.findById(userA.id);

        assertEquals(1, userA.getFollowing().size());
        assertEquals(1, userService.getFollowingCount(userA.id));

        /*
         * We are even telling it to persist and flush explicitly
         */
        userService.unfollow(userA.id, userB.id, true);

        /*
         * Reloading the user after changes made
         */
        userA = userRepository.findById(1L);

        /*
         * This call succeeds - the database has been updated
         */
        assertEquals(0, userService.getFollowingCount(userA.id)); // succeeds

        /*
         * This call fails - the Panache Repository has not loaded the changes made to the database
         */
        assertEquals(0, userA.getFollowing().size());
    }


    @AfterEach
    @Transactional
    void tearDown() {
        cleanFollowerTable();
    }

    /**
     * TODO Side-question: Why do tearDown and setUp have to be annotated with @Transactional and not just the cleanFollowerTable method?
     */
    //@Transactional
    @SuppressWarnings("SqlWithoutWhere")
    private void cleanFollowerTable() {
        entityManager
                .createNativeQuery("delete from followers")
                .executeUpdate();
    }
}