package th_prj.th_company.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import th_prj.th_company.member.Permission;

@Configuration
public class SecurityConfig {

	private static final String ADMIN = "ROLE_ADMIN";

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/login", "/css/**", "/js/**", "/images/**", "/favicon.ico", "/error").permitAll()
				// 관리 권한(Permission)이 있으면 관리자 화면 중 자기 기능만 쓸 수 있다. 관리자는 모두.
				.requestMatchers("/admin").authenticated()
				.requestMatchers("/admin/boards/**").hasAnyAuthority(ADMIN, Permission.BOARD_MANAGER.authority())
				.requestMatchers("/admin/approval-templates/**").hasAnyAuthority(ADMIN, Permission.APPROVAL_MANAGER.authority())
				.requestMatchers("/admin/rooms/**").hasAnyAuthority(ADMIN, Permission.ROOM_MANAGER.authority())
				.requestMatchers("/admin/resources/**").hasAnyAuthority(ADMIN, Permission.RESOURCE_MANAGER.authority())
				.requestMatchers("/admin/**").hasAuthority(ADMIN)
				.requestMatchers("/attendance/manage/**").hasAnyAuthority(ADMIN, Permission.ATTENDANCE_MANAGER.authority())
				.anyRequest().authenticated())
			.formLogin(form -> form
				.loginPage("/login")
				.usernameParameter("username")
				.passwordParameter("password")
				.defaultSuccessUrl("/", true)
				.failureUrl("/login?error")
				.permitAll())
			.logout(logout -> logout
				.logoutUrl("/logout")
				.logoutSuccessUrl("/login?logout")
				.permitAll());
		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		// 기본 bcrypt, 저장값에 {bcrypt} 접두어 포함
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}
}
