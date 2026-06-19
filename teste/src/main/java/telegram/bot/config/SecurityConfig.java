package telegram.bot.config;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
/**
 * Configuração de segurança da aplicação Web.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /** Senha padrão de desenvolvimento — NUNCA deve ser usada em produção. */
    private static final String SENHA_PADRAO = "admin123";

    @Value("${app.security.username:admin}")
    private String username;

    @Value("${app.security.password:admin123}")
    private String password;

    /**
     * Emite um aviso bem visível no boot se a senha continuar sendo o default
     * de desenvolvimento, alertando para configurar APP_PASSWORD em produção.
     * Não interrompe o startup para não quebrar o ambiente de dev.
     */
    @PostConstruct
    public void avisarSenhaPadrao() {
        if (SENHA_PADRAO.equals(password)) {
            log.warn("****************************************************************");
            log.warn("* ATENCAO: a senha de acesso a UI esta usando o DEFAULT        *");
            log.warn("* '{}'. Configure APP_PASSWORD com um valor forte antes   *", SENHA_PADRAO);
            log.warn("* de expor a aplicacao em PRODUCAO!                            *");
            log.warn("****************************************************************");
        }
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        UserDetails admin = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(admin);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/webhook/**", "/h2-console/**")
            )
            // sameOrigin mantém o console H2 funcionando em dev sem expor o site
            // inteiro a clickjacking (como faria frameOptions().disable()).
            .headers(h -> h.frameOptions(f -> f.sameOrigin()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/css/**", "/js/**", "/webhook/**", "/h2-console/**").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            );

        return http.build();
    }
}
