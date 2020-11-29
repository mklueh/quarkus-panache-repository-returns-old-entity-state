package com.quarkus.demo;


import io.quarkus.hibernate.orm.panache.PanacheEntity;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "user_")
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class User extends PanacheEntity {


    @Column(name = "name")
    private String name;


    @EqualsAndHashCode.Include
    public Long getId() {
        return id;
    }

    @ManyToMany(cascade = CascadeType.ALL, mappedBy = "following")
    private Set<User> followers = new HashSet<>();

    @JoinTable(name = "followers",
            joinColumns = {@JoinColumn(name = "user_id")},
            inverseJoinColumns = {@JoinColumn(name = "follower_id")})
    @ManyToMany(cascade = CascadeType.ALL)
    private Set<User> following = new HashSet<>();

    public void addFollower(User toFollow) {
        following.add(toFollow);
        toFollow.getFollowers().add(this);
    }

    public void removeFollower(User toFollow) {
        following.remove(toFollow);
        toFollow.getFollowers().remove(this);
    }

}
