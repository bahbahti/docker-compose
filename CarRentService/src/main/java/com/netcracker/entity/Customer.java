package com.netcracker.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.util.List;

@Entity
@Table(name = "customers",
uniqueConstraints = {@UniqueConstraint(columnNames = {"first_name", "last_name"})}
)
@Data
@NoArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "area_of_living", nullable = false)
    private String areaOfLiving;

    @Column(name = "discount", nullable = true)
    private Integer discount;

    @Column(name = "passport_number", nullable = false, unique = true)
    private Integer passportNumber;

    @Column(name = "phone_number", nullable = true)
    private Integer phoneNumber;

    @Column(name = "username", nullable = false, unique = true)
    private String username;

    @Column(name = "password", nullable = false)
    private String password;

    @OneToMany(mappedBy = "customerId", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER, targetEntity = Order.class)
    private List<Order> orders;

    @ManyToOne(targetEntity = Role.class)
    private Role role;

    public Customer(Customer customer) {
        this.id = customer.getId();
        this.firstName = customer.getFirstName();
        this.lastName = customer.getLastName();
        this.areaOfLiving = customer.getAreaOfLiving();
        this.discount = customer.getDiscount();
        this.passportNumber = customer.getPassportNumber();
        this.username = customer.getUsername();
        this.password = customer.getPassword();
        this.role = customer.getRole();
    }

}
