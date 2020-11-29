package com.quarkus.demo;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.transaction.Transactional;
import java.math.BigInteger;

@ApplicationScoped
public class UserService {

    @Inject
    EntityManager em;

    @Inject
    UserRepository userRepository;

    public void follow(Long userId, Long toFollowId) {
        follow(userId, toFollowId, false);
    }

    @Transactional
    public void follow(Long userId, Long toFollowId, boolean withPersistAndFlush) {
        User user = userRepository.findById(userId);
        User toFollow = userRepository.findById(toFollowId);
        user.addFollower(toFollow);

        if (withPersistAndFlush) {
            user.persistAndFlush();
            toFollow.persistAndFlush();
        }
    }

    public void unfollow(Long userId, Long toUnfollowId) {
        unfollow(userId, toUnfollowId, false);
    }

    @Transactional
    public void unfollow(Long userId, Long toUnfollowId, boolean withPersistAndFlush) {
        User user = userRepository.findById(userId);
        User toFollow = userRepository.findById(toUnfollowId);
        user.removeFollower(toFollow);

        if (withPersistAndFlush) {
            user.persistAndFlush();
            toFollow.persistAndFlush();
        }
    }

    @Transactional
    public Long getFollowingCount(Long userId) {
        return getFollowTableCount(userId, "select count(*) from followers where user_id = :id");
    }

    @Transactional
    public Long getFollowerCount(Long userId) {
        return getFollowTableCount(userId, "select count(*) from followers where follower_id = :id");
    }

    private Long getFollowTableCount(Long userId, String s) {
        Query query = em.createNativeQuery(s)
                .setParameter("id", userId);

        BigInteger singleResult = (BigInteger) query.getSingleResult();

        return singleResult != null ? singleResult.longValue() : 0;
    }
}
