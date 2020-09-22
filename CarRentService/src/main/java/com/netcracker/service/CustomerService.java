package com.netcracker.service;

import com.netcracker.entity.Customer;
import com.netcracker.entity.CustomerUserDetails;
import com.netcracker.repository.CustomerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomerService implements UserDetailsService {

    @Autowired
    private CustomerRepository customerRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Customer customer = customerRepository.findCustomerByUsername(username).orElseThrow(() ->
                new UsernameNotFoundException("user " + username + " was not found"));
        return new CustomerUserDetails(customer);
    }
}
