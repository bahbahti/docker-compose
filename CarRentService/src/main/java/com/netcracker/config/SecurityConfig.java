package com.netcracker.config;

import com.netcracker.service.CustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Autowired
    private CustomerService customerService;

    @Bean
    public PasswordEncoder bcryptPasswordEncoder () {
        return new BCryptPasswordEncoder();
    }

    @Autowired
    private void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
        auth
                .userDetailsService(customerService)
                .passwordEncoder(bcryptPasswordEncoder());
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .csrf().disable()
                .authorizeRequests()
                .anyRequest().authenticated()
                .antMatchers("/customers/paidOrders", "/customers/activeOrders").hasAnyAuthority("USER", "ADMIN")
                .antMatchers("/cars/**", "/customers/**", "/orders/**", "/repairOrders/**").hasAuthority("ADMIN")
                //.antMatchers("/**").hasAuthority("ADMIN")
                .and()
                .formLogin().permitAll()
                .defaultSuccessUrl("/swagger-ui.html")
                .and()
                .logout().permitAll();
    }

}
